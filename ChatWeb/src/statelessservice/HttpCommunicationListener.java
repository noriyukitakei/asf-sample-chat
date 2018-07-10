// ------------------------------------------------------------
//  Copyright (c) Microsoft Corporation.  All rights reserved.
//  Licensed under the MIT License (MIT). See License.txt in the repo root for license information.
// ------------------------------------------------------------

package statelessservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import microsoft.servicefabric.services.client.ServicePartitionKey;
import microsoft.servicefabric.services.communication.client.TargetReplicaSelector;
import microsoft.servicefabric.services.communication.runtime.CommunicationListener;
import microsoft.servicefabric.services.remoting.client.ServiceProxyBase;
import microsoft.servicefabric.services.runtime.StatelessServiceContext;
import rpcmethods.ChatMessage;
import rpcmethods.ChatRPC;
import system.fabric.CancellationToken;

public class HttpCommunicationListener implements CommunicationListener {

	private static final Logger logger = Logger.getLogger(HttpCommunicationListener.class.getName());

	private static final String HEADER_CONTENT_TYPE = "Content-Type";
	private static final int STATUS_OK = 200;
	private static final int STATUS_NOT_FOUND = 404;
	private static final int STATUS_ERROR = 500;
	private static final String RESPONSE_NOT_FOUND = "404 (Not Found) \n";
	private static final String ENCODING = "UTF-8";

	private StatelessServiceContext context;
	private com.sun.net.httpserver.HttpServer server;
	private ServicePartitionKey partitionKey;
	private final int port;

	// このクラスは、Stateless ServiceがHTTPのエンドポイントをもつための
	// リスナーを構成するクラスです。CommunicationListenerインターフェースを実装する必要があります。
	// コンストラクタの引数には、Stateless Serviceの
	// 設定情報などを保持しているStatelessServiceContextとHTTPエンドポイントの
	// ポート番号を指定します。
	public HttpCommunicationListener(StatelessServiceContext context, int port) {
		this.partitionKey = new ServicePartitionKey(0);
		this.context = context;
		this.port = port;
	}

	// このクラスがimplementsしているCommunicationListenerインターフェースが提供している
	// openAsyncメソッドから呼ばれるメソッドで、Javaが標準で持っているWebサーバー(com.sun.net.httpserver)を
	// 起動する処理です。
	public void start() {
		try {
			logger.log(Level.INFO, "Starting Server");
			// Chat/ChatApplication/ChatWebPkg/ServiceManifest.xmlで定義したポートで
			// Webサーバーを起動します。
			server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(this.port), 0);
		} catch (Exception ex) {
			logger.log(Level.SEVERE, null, ex);
			throw new RuntimeException(ex);
		}

		// URL毎のハンドラを定義します。
		// 他のどのハンドラにもマッチしないものはここに来ます。
		// チャットのトップ画面やアイコン画像にアクセスした際に呼ばれます。
		server.createContext("/", new HttpHandler() {
			@Override
			public void handle(HttpExchange t) {
				OutputStream os = null;
				FileInputStream fs = null;

				try {
					File file;
					if (t.getRequestURI().toString().equals("/")) {
						// URLのロケーションが/だったら、index.htmlを呼び出します。
						file = new File("wwwroot/index.html").getCanonicalFile();
					} else if (t.getRequestURI().toString().startsWith("/img/")) {
						// ここの分岐に来るものは、URLのロケーションが/img/から始まるものです。
						// 例えば、/img/ntakei.pngというロケーションの場合、
						// wwwroot/img/ntakei.pngのファイルの存在を確認し、もしなければ、
						// wwwroot/img/other.pngに置き換えます。つまり、wwwroot/img配下には、
						// ユーザー名に紐付いたアイコン画像が置いてあるのですが、もし、それがない場合は、
						// 全部wwwroot/img/other.pngに置き換えるということをやっています。
						file = new File("wwwroot" + t.getRequestURI().toString()).getCanonicalFile();
						if (!file.exists()) {
							file = new File("wwwroot/img/other.png").getCanonicalFile();
						}
					} else {
						// 上記以外のファイルは、普通に指定されたパスでFileオブジェクトを作成します。
						file = new File("wwwroot" + t.getRequestURI().toString()).getCanonicalFile();
					}

					if (!file.exists()) {
						// URLのロケーションで指定されたファイルがない場合は、404を返します。
						t.sendResponseHeaders(STATUS_NOT_FOUND, RESPONSE_NOT_FOUND.length());
						os = t.getResponseBody();
					} else {
						// URLのロケーションで指定されたファイルがある場合、
						// そのContent-Typeを取得します。
						Path path = Paths.get(file.getAbsolutePath());
						String contentType = Files.probeContentType(path);

						// 取得したContent-TypeのHTTPヘッダを送信します。
						// また合わせて200のHTTPステータスコードも返します。
						Headers h = t.getResponseHeaders();
						h.set(HEADER_CONTENT_TYPE, contentType);
						t.sendResponseHeaders(STATUS_OK, 0);

						// ファイルから一定のバイト数ずつ読み込み
						// HTTPレスポンスとして送信します。
						os = t.getResponseBody();
						fs = new FileInputStream(file);
						final byte[] buffer = new byte[0x10000];
						int count = 0;
						while ((count = fs.read(buffer)) >= 0) {
							os.write(buffer, 0, count);
						}
					}

				} catch (Exception e) {
					logger.log(Level.WARNING, null, e);
				} finally {
					try {
						fs.close();
						os.close();
					} catch (IOException e) {
						logger.log(Level.WARNING, null, e);
					}
				}
			}
		});

		// メッセージを投稿したときに呼び出されるハンドラです。
		server.createContext("/addMessage", new HttpHandler() {
			@Override
			public void handle(HttpExchange t) {
				try {
					// HTTPリクエストのボディを読み出す処理です。
					InputStream is = t.getRequestBody();

					BufferedReader br = new BufferedReader(new InputStreamReader(is));

					StringBuilder sb = new StringBuilder();

					String requestBody;

					while ((requestBody = br.readLine()) != null) {
						sb.append(requestBody);
					}

					// HTTPリクエストのボディは、Postメソッドのapplication/x-www-form-urlencoded形式です。
					// よって、ユーザーがntakei、メッセージがhelloだとすると、
					// name=ntakei&messege=helloというボディになります。
					// それをqueryToMapメソッドで、ntakeiとhelloだけ取り出しています。
					Map<String, String> params = queryToMap(sb.toString());

					// ユーザー名とメッセージが日本語の場合の処理です。
					String name = URLDecoder.decode(params.get("name"), "UTF-8");
					String message = URLDecoder.decode(params.get("message"), "UTF-8");

					// ChatRPCプロジェクトで定義してあるChatMessageオブジェクトに
					// ユーザー名とメッセージ、投稿日を格納します。
					ChatMessage chatMessage = new ChatMessage();
					chatMessage.setName(name);
					chatMessage.setMessage(message);
					chatMessage.setPubDate(System.currentTimeMillis());

					OutputStream os = t.getResponseBody();
					
					// RPC経由でCahtDataのStateful Serviceを呼び出しています。
					// createメソッドの第1引数はRPCでやり取りする通信のインターフェースを定義します。
					// ChatRPC.classは、microsoft.servicefabric.services.remoting.Serviceインターフェースを
					// implementsしている必要があり、その実装はChatDataプロジェクトのChatDataクラスで行われます。
					// 第2引数は、呼び出すサービスのURI、つまりサービスを一意に識別する
					// 名前や住所みたいなものを指定します。 書式はfabric:/[アプリケーション名]/[サービス名]です。
					// アプリケーション名は、一番最初にAzure Service Fabricクラスターにアプリケーションを
					// 作成するときにsfctlコマンドで指定します。Eclipseプラグインを使うと、自動的に
					// プロジェク名 + Applicationという名称でsfctlコマンドで作成されます。サービス名もEclipseプラグインを使って
					// サービスを作成するときに決定され、そのサービス名は、Chat/ChatApplication/ApplicationManifest.xmlに
					// 記録されています。createメソッドの戻り値はcreateメソッドの第一引数で指定したChatRPCに
					// なるので、ChatRPCで定義されているaddMessageを呼び出して、メッセージをReliableHashMapに登録します。
					// addMessageの戻り値はCompletableFutureで取得できるIntegerなので、最後にgetしてます。
					// 戻り値が1ならば成功、-1なら失敗です。
					Integer num = ServiceProxyBase.create(ChatRPC.class, new URI("fabric:/ChatApplication/ChatData"),
							partitionKey, TargetReplicaSelector.DEFAULT, "").addMessage(chatMessage).get();
					
					if (num != 1) {
						// 失敗のときは、HTTPステータスコード500を返します。
						t.sendResponseHeaders(STATUS_ERROR, 0);
					} else {
						// 成功のときは、HTTPステータスコード200を返します。
						t.sendResponseHeaders(STATUS_OK, 0);
					}

					// レスポンスのボディに、RPCの戻り値をJSONに変換して返します。
					// でも実際はこの値を見ることはないです。
					// エラーかどうかはHTTPステータスコードをみればわかるので。
					String json = new Gson().toJson(num);
					os.write(json.getBytes(ENCODING));
					os.close();
				} catch (Exception e) {
					logger.log(Level.WARNING, null, e);
				}
			}
		});

		// メッセージの一覧を取得したときに呼び出されるハンドラです。
		server.createContext("/getMessageList", new HttpHandler() {
			@Override
			public void handle(HttpExchange t) {
				try {
					t.sendResponseHeaders(STATUS_OK, 0);
					OutputStream os = t.getResponseBody();

					// addMessageのところと同じ処理なので説明は割愛します。
					HashMap<String, ChatMessage> map = ServiceProxyBase
							.create(ChatRPC.class, new URI("fabric:/ChatApplication/ChatData"), partitionKey,
									TargetReplicaSelector.DEFAULT, "")
							.getMessageList().get();

					// RPC経由でChatDataサービスから取得したメッセージの一覧を
					// 発言日付順に並び替えています。
					ArrayList<ChatMessage> entries = new ArrayList(map.values());
					Collections.sort(entries, new Comparator<ChatMessage>() {
						public int compare(ChatMessage obj1, ChatMessage obj2) {
							return obj1.getPubDate().intValue() - obj2.getPubDate().intValue();
						}
					});

					String json = new Gson().toJson(entries);
					os.write(json.getBytes(ENCODING));
					os.close();
				} catch (Exception e) {
					logger.log(Level.WARNING, null, e);
				}
			}
		});

		// Webサーバーを起動します。
		server.setExecutor(null);
		server.start();
	}

	// Content-Typeがapplication/x-www-form-urlencodedの形式の
	// HTTPリクエストボディをparseして、Mapに詰め込みます。
	private Map<String, String> queryToMap(String query) {
		Map<String, String> result = new HashMap<String, String>();
		for (String param : query.split("&")) {
			String pair[] = param.split("=");
			if (pair.length > 1) {
				result.put(pair[0], pair[1]);
			} else {
				result.put(pair[0], "");
			}
		}
		return result;
	}

	// Webサーバーを停止する処理です。
	private void stop() {
		if (null != server)
			server.stop(0);
	}

	// サービスが開始するときに呼ばれるメソッドです。
	// このクラスのstartメソッドを呼び出し、戻り値として、エンドポイントのURLを返します。
	@Override
	public CompletableFuture<String> openAsync(CancellationToken cancellationToken) {
		this.start();
		logger.log(Level.INFO, "Opened Server");
		String publishUri = String.format("http://%s:%d/", this.context.getNodeContext().getIpAddressOrFQDN(), port);
		return CompletableFuture.completedFuture(publishUri);
	}

	// サービスが停止するときに呼ばれるメソッドです。
	// このクラスのstopメソッドを呼び出し、戻り値として、trueを返します。
	@Override
	public CompletableFuture<?> closeAsync(CancellationToken cancellationToken) {
		this.stop();
		return CompletableFuture.completedFuture(true);
	}

	@Override
	public void abort() {
		this.stop();
	}
}
