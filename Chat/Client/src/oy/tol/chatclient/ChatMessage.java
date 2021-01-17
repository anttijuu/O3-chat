package oy.tol.chatclient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.json.JSONObject;

public class ChatMessage {
	public LocalDateTime sent;
	public String nick;
	public String message;
	public boolean useColors = true;

	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_BLACK = "\u001B[30m";
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_BLUE = "\u001B[34m";
	public static final String ANSI_PURPLE = "\u001B[35m";
	public static final String ANSI_CYAN = "\u001B[36m";
	public static final String ANSI_WHITE = "\u001B[37m";

	static public ChatMessage from(JSONObject jsonObject) {
		ChatMessage message = new ChatMessage();
		message.nick = jsonObject.getString("user");
		String dateStr = jsonObject.getString("sent");
		OffsetDateTime odt = OffsetDateTime.parse(dateStr);
		message.sent = LocalDateTime.ofInstant(odt.toInstant(), ZoneId.systemDefault());
		message.message = jsonObject.getString("message");
		return message;
	}
	
	public String toString() {
		String str = "";
		LocalDateTime now = LocalDateTime.now();
		long diff = Math.abs(ChronoUnit.HOURS.between(now, sent));
		if (useColors) {
			str += ANSI_GREEN;
		}
		if (diff <= 24) {
			str += sent.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		} else {
			str += sent.format(DateTimeFormatter.ofPattern("yyyy.MM.dd hh:mm:ss"));
		}
		if (useColors) {
			str += ANSI_BLUE;
		}
		str += " " + nick + ": ";
		if (useColors) {
			str += ANSI_CYAN;
		} 
		str += message + ANSI_RESET;
		return str;
	}
	
}
