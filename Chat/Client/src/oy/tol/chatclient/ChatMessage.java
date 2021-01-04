package oy.tol.chatclient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.json.JSONObject;

public class ChatMessage {
	public LocalDateTime sent;
	private String nick;
	private String message;
	
	static public ChatMessage from(JSONObject jsonObject) {
		ChatMessage message = new ChatMessage();
		message.nick = jsonObject.getString("user");
		String dateStr = jsonObject.getString("sent");
		OffsetDateTime odt = OffsetDateTime.parse(dateStr);
		message.sent = odt.toLocalDateTime();
		message.message = jsonObject.getString("message");
		return message;
	}
	
	public String toString() {
		String str = "";
		LocalDateTime now = LocalDateTime.now();
		long diff = Math.abs(ChronoUnit.HOURS.between(now, sent));
		if (diff <= 24) {
			str += sent.format(DateTimeFormatter.ISO_LOCAL_TIME);
		} else {
			str += sent.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		}
		str += " " + nick + ": " + message;
		return str;
	}
	
}
