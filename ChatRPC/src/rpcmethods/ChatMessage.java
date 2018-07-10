package rpcmethods;

import java.io.Serializable;

// チャットのメッセージや、そのメッセージを
// 発言したユーザー名などを格納するJava Beanです。
public class ChatMessage implements Serializable{
	
	private static final long serialVersionUID = 1L;
	private String name; // メッセージを発言したユーザー名
	private String message; // メッセージの内容
	private Long pubDate; // メッセージを発言した日時
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		return super.hashCode();
	}
	public Long getPubDate() {
		return pubDate;
	}
	public void setPubDate(Long pubDate) {
		this.pubDate = pubDate;
	}
	

}
