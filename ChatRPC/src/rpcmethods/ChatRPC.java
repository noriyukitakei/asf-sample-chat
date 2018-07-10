package rpcmethods; 

import java.util.concurrent.CompletableFuture;
import java.util.HashMap;

import microsoft.servicefabric.services.remoting.Service;

// RPCで呼び出されるメソッドを定義したインターフェースです。
public interface ChatRPC extends Service {
	// メッセージを発言する処理のインターフェースです。
	// CompletableFutureにしていますが、今回では、このメソッドを非同期で
	// 呼び出す処理はありません。将来的に非同期処理にも対応できるよう
	// とりあえずCompletableFutureにしています。戻り値は、
	// 成功したら1、失敗したら−1を返します。
	CompletableFuture<Integer> addMessage(ChatMessage message);

	// 現時点でReliableHashMapに格納されているメッセージを返します。
	CompletableFuture<HashMap<String, ChatMessage>> getMessageList();

}