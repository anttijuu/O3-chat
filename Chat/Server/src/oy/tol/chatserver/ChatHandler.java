package oy.tol.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ChatHandler implements HttpHandler {

	private class ChatMessage {
		public LocalDateTime sent;
		public String nick;
		public String message;
	}
	// TODO: change to arraylist and add sort like in client, to keep msgs in date order.
	private Map<Long, ChatMessage> messages = new Hashtable<Long,ChatMessage>();
	private long messageNum = 0;
	
	// private ArrayList<String> messages = new ArrayList<String>();
	private String responseBody = "";
	
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		int code = 200;
		
		if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
			System.out.println("New chatmessage in HTTP POST.");
			code = handleChatMessageFromClient(exchange);
		} else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
			System.out.println("HTTP GET for chats");
			code = handleGetRequestFromClient(exchange);
		} else {
			code = 400;
			responseBody = "Not supported.";
		}
		if (code < 200 || code > 299) {
			System.out.println("*** Error in /chat: " + code + " " + responseBody);
			byte [] bytes = responseBody.getBytes("UTF-8");
			exchange.sendResponseHeaders(code, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
	}
	
	private int handleChatMessageFromClient(HttpExchange exchange) throws IOException {
		int code = 200;
		Headers headers = exchange.getRequestHeaders();
		int contentLength = 0;
		String contentType = "";
		if (headers.containsKey("Content-Length")) {
			contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
		}
		if (headers.containsKey("Content-Type")) {
			contentType = headers.get("Content-Type").get(0);
		}
		if (contentType.equalsIgnoreCase("application/json")) {
			InputStream stream = exchange.getRequestBody();
			String json = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				        .lines()
				        .collect(Collectors.joining("\n"));
			System.out.println(json);
			stream.close();
			if (json.length() > 0) {
				// TODO process within try catch
				if (processMessage(json)) {
					exchange.sendResponseHeaders(code, -1);
					System.out.println("New chatmessage handled, messages: " + messages.size());
				} else {
					code = 400;
					responseBody = "Corrupted message.";
				}
			} else {
				code = 400;
				responseBody = "No content in request";
			}
		} else {
			code = 411;
			responseBody = "Content-Type must be application/json.";
		}
		return code;
	}
	
	private boolean processMessage(String json) {
		JSONObject jsonObject = new JSONObject(json);
		ChatMessage newMessage = new ChatMessage();
		newMessage.nick = jsonObject.getString("user");
		String dateStr = jsonObject.getString("sent");
		OffsetDateTime odt = OffsetDateTime.parse(dateStr);
		newMessage.sent = odt.toLocalDateTime();
		newMessage.message = jsonObject.getString("message");
		messages.put(messageNum++, newMessage);
		return true;
	}
	
	private int handleGetRequestFromClient(HttpExchange exchange) throws IOException {
		int code = 200;
		
		if (messages.isEmpty()) {
			code = 204;
			exchange.sendResponseHeaders(code, 0);
			return code;
		}
		
		JSONArray responseMessages = new JSONArray();
		
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
		
		for (Map.Entry<Long,ChatMessage> message : messages.entrySet()) {
			JSONObject jsonMessage = new JSONObject();
			jsonMessage.put("message", message.getValue().message);
			jsonMessage.put("user", message.getValue().nick);
			LocalDateTime date = message.getValue().sent;
			ZonedDateTime toSend = ZonedDateTime.of(date, ZoneId.of("UTC"));
			String dateText = toSend.format(formatter);
			jsonMessage.put("sent", dateText);
			responseMessages.put(jsonMessage);
		}
		byte [] bytes = responseMessages.toString().getBytes("UTF-8");
		exchange.sendResponseHeaders(code, bytes.length);
		OutputStream os = exchange.getResponseBody();
		os.write(bytes);
		os.close();
		return code;
	}
	
}
