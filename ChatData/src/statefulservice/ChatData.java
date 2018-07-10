package statefulservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import microsoft.servicefabric.data.ReliableStateManager;
import microsoft.servicefabric.data.Transaction;
import microsoft.servicefabric.data.collections.ReliableHashMap;
import microsoft.servicefabric.data.utilities.AsyncEnumeration;
import microsoft.servicefabric.data.utilities.KeyValuePair;
import microsoft.servicefabric.services.communication.runtime.ServiceReplicaListener;
import microsoft.servicefabric.services.remoting.fabrictransport.runtime.FabricTransportServiceRemotingListener;
import microsoft.servicefabric.services.runtime.StatefulService;
import rpcmethods.ChatMessage;
import rpcmethods.ChatRPC;
import system.fabric.StatefulServiceContext;

class ChatData extends StatefulService implements ChatRPC {
	private static final Logger logger = Logger.getLogger(ChatData.class.getName());
	private static final String MAP_NAME = "messagesMap";
	private ReliableStateManager stateManager;

	// コンストラクタの引数には、Stateful Serviceの
	// 設定情報などを保持しているStatefulServiceContextを渡し、
	// 継承元のコンストラクタをそのまま呼びます。
	protected ChatData(StatefulServiceContext statefulServiceContext) {
		super(statefulServiceContext);
	}

	@Override
	protected List<ServiceReplicaListener> createServiceReplicaListeners() {

		// ReliableHashMapの状態(トランザクションなど)を管理するための
		// ReliableStateManagerを取得します。
		this.stateManager = this.getReliableStateManager();
		ArrayList<ServiceReplicaListener> listeners = new ArrayList<>();

		// RPCのリクエストを待ち受けるためのリスナー(ServiceReplicaListener)を作成します。
		listeners.add(new ServiceReplicaListener((context) -> {
			return new FabricTransportServiceRemotingListener(context, this);
		}));

		return listeners;
	}

	// ChatRPCインターフェースで定義してあるaddMessageの実装で、
	// ユーザーのメッセージをReliableHashMapに登録します。
	// 戻り値は、成功したら1、失敗したら-1を返します。
	@Override
	public CompletableFuture<Integer> addMessage(ChatMessage message) {
		AtomicInteger status = new AtomicInteger(-1);

		try {

			// 現時点でAzure Service Fabricクラスターに登録されているReliableHashMapを取得します。
			ReliableHashMap<String, ChatMessage> messagesMap = stateManager
					.<String, ChatMessage>getOrAddReliableHashMapAsync(MAP_NAME).get();

			// トランザクションを開始します。
			Transaction tx = stateManager.createTransaction();

			// ReliableHashMapを更新します。computeAsyncメソッドの第一引数は先程取得したトランザクション、
			// 第2引数はHashMapのキー、第3引数はHashMapのキー、値を引数としたラムダ関数です。
			// 第2引数で指定したキー及びその値が(k,v)にマッピングされます。
			// すでに存在するキーを指定したらそのキーのMapを更新、存在しないキーを指定したら
			// 新しいキー、値のペアをHashMapに追加します。ここでは、メッセージの登録なので、
			// 基本的にいつでも追加なので、本メソッドの引数で指定されたmessage(ChatMessage型)を
			// 返しています。computeAsyncの戻り値はCompletableFutureなので、getメソッドで、
			// 処理を実行します。
			messagesMap.computeAsync(tx, Integer.toString(message.hashCode()), (k, v) -> {
				return message;
			}).get();

			// トランザクションをコミットしています。
			tx.commitAsync().get();

			// トランザクションをクローズしています。
			tx.close();

			// 処理が成功したので1をセットしています。
			status.set(1);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// 実行結果を返します。
		return CompletableFuture.completedFuture(new Integer(status.get()));
	}

	// ChatRPCインターフェースで定義してあるgetMessageListの実装で、
	// ユーザーのメッセージが格納されたReliableHashMapに返します。
	@Override
	public CompletableFuture<HashMap<String, ChatMessage>> getMessageList() {
		HashMap<String, ChatMessage> tempMap = new HashMap<String, ChatMessage>();

		try {
			// 現時点でAzure Service Fabricクラスターに登録されているReliableHashMapを取得します。
			ReliableHashMap<String, ChatMessage> messagesMap = stateManager
					.<String, ChatMessage>getOrAddReliableHashMapAsync(MAP_NAME).get();

			// トランザクションを開始します。
			Transaction tx = stateManager.createTransaction();

			// ReliableHashMapを普通のHashMapに変換します。
			AsyncEnumeration<KeyValuePair<String, ChatMessage>> kv = messagesMap.keyValuesAsync(tx).get();
			while (kv.hasMoreElementsAsync().get()) {
				KeyValuePair<String, ChatMessage> k = kv.nextElementAsync().get();
				tempMap.put(k.getKey(), k.getValue());
			}

			// トランザクションをクローズします。
			tx.close();

		} catch (Exception e) {
			e.printStackTrace();
		}

		return CompletableFuture.completedFuture(tempMap);
	}

}