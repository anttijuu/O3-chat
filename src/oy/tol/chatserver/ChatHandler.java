package oy.tol.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class ChatHandler implements HttpHandler {
	
	private String responseBody = "";
	
	private static final DateTimeFormatter jsonDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
	private static final DateTimeFormatter httpDateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss.SSS z", Locale.ENGLISH).withZone(ZoneId.of("GMT"));
	
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		int code = 200;
		try {
			if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
				code = handleChatMessageFromClient(exchange);
			} else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
				code = handleGetRequestFromClient(exchange);
			} else {
				code = 400;
				responseBody = "Not supported.";
			}
		} catch (JSONException e) {
			code = 400;
			responseBody = "Invalid JSON in request";
		} catch (SQLException e ) {
			code = 400;
			responseBody = "Database error in saving chat message";
		} catch (IOException e) {
			code = 500;
			responseBody = "Error in handling the request: " + e.getMessage();
		} catch (Exception e) {
			code = 500;
			responseBody = "Server error: " + e.getMessage();
		}
		if (code < 200 || code > 299) {
			ChatServer.log("*** Error in /chat: " + code + " " + responseBody);
			byte [] bytes = responseBody.getBytes("UTF-8");
			exchange.sendResponseHeaders(code, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
	}
	
	private int handleChatMessageFromClient(HttpExchange exchange) throws Exception {
		int code = 200;
		Headers headers = exchange.getRequestHeaders();
		int contentLength = 0;
		String contentType = "";
		if (headers.containsKey("Content-Length")) {
			contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
		} else {
			code = 411;
			return code;
		}
		if (headers.containsKey("Content-Type")) {
			contentType = headers.get("Content-Type").get(0);
		} else {
			code = 400;
			responseBody = "No content type in request";
			return code;
		}
		String user = exchange.getPrincipal().getUsername();
		String expectedContentType = "application/json";
		if (ChatServer.version < 3) {
			expectedContentType = "text/plain";
		}
		if (contentType.equalsIgnoreCase(expectedContentType)) {
			InputStream stream = exchange.getRequestBody();
			String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				        .lines()
				        .collect(Collectors.joining("\n"));
			ChatServer.log(text);
			stream.close();
			if (text.trim().length() > 0) {
				exchange.sendResponseHeaders(code, -1);
				processMessage(user, text);
				ChatServer.log("New chatmessage saved");
			} else {
				code = 400;
				responseBody = "No content in request";
				ChatServer.log(responseBody);
			}
		} else {
			code = 411;
			responseBody = "Content-Type must be application/json.";
			ChatServer.log(responseBody);
		}
		return code;
	}
	
	private void processMessage(String user, String text) throws JSONException, SQLException {
		if (ChatServer.version >= 3) {
			JSONObject jsonObject = new JSONObject(text);
			ChatMessage newMessage = new ChatMessage();
			newMessage.nick = jsonObject.getString("user");
			String dateStr = jsonObject.getString("sent");
			OffsetDateTime odt = OffsetDateTime.parse(dateStr);
			newMessage.sent = odt.toLocalDateTime();
			newMessage.message = jsonObject.getString("message");
			ChatDatabase.getInstance().insertMessage(user, newMessage);
		} else {
			ChatMessage newMessage = new ChatMessage();
			newMessage.nick = user;
			newMessage.message = text;
			LocalDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).toLocalDateTime();
			newMessage.sent = now;
			ChatDatabase.getInstance().insertMessage(user, newMessage);
		}
	}
	
	private int handleGetRequestFromClient(HttpExchange exchange) throws IOException, SQLException {
		int code = 200;
		
		Headers requestHeaders = exchange.getRequestHeaders();
		LocalDateTime messagesSince = null;
		if (requestHeaders.containsKey("If-Modified-Since")) {
			String requestSinceString = requestHeaders.getFirst("If-Modified-Since");
			ChatServer.log("Client wants messages from " + requestSinceString);
			ZonedDateTime odt = ZonedDateTime.parse(requestSinceString, httpDateFormatter);
			messagesSince = odt.toLocalDateTime();
		} else {
			ChatServer.log("No If-Modified-Since header in request");
		}

		long messagesSinceLong = -1;
		if (null != messagesSince) {
			messagesSinceLong = messagesSince.toInstant(ZoneOffset.UTC).toEpochMilli();
			ChatServer.log("Wants since: " + messagesSince);
		}
		List<ChatMessage> messages = ChatDatabase.getInstance().getMessages(messagesSinceLong);
		if (null == messages) {
			ChatServer.log("No new messages to deliver to client");
			code = 204;
			exchange.sendResponseHeaders(code, -1);			
			return code;
		}
		JSONArray responseMessages = new JSONArray();
		ZonedDateTime newest = null;
		// Used if no JSON yet:
		List<String> plainList = null;
		for (ChatMessage message : messages) {
			boolean includeThis = false;
			if (null == messagesSince || (messagesSince.isBefore(message.sent))) {
				includeThis = true;
			}
			if (includeThis) {
				if (ChatServer.version >= 3) {
					JSONObject jsonMessage = new JSONObject();
					jsonMessage.put("message", message.message);
					jsonMessage.put("user", message.nick);
					LocalDateTime date = message.sent;
					ZonedDateTime toSend = ZonedDateTime.of(date, ZoneId.of("UTC"));
					if (null == newest) {
						newest = toSend;
					} else {
						if (toSend.isAfter(newest)) {
							newest = toSend;
						}
					}
					String dateText = toSend.format(jsonDateFormatter);
					jsonMessage.put("sent", dateText);
					responseMessages.put(jsonMessage);
				} else {
					if (null == plainList) {
						plainList = new ArrayList<String>();
					}
					plainList.add(message.message);
				}

			}
		}
		boolean isEmpty = false;
		if (ChatServer.version >= 3) {
			if (responseMessages.isEmpty()) {
				isEmpty = true;
			}
		} else if (null == plainList) {
			isEmpty = true;
		}
		if (isEmpty) {
			ChatServer.log("No new messages to deliver to client since last request");
			code = 204;
			exchange.sendResponseHeaders(code, -1);
		} else {
			ChatServer.log("Delivering " + responseMessages.length() + " messages to client");
			if (null != newest && ChatServer.version >= 5) {
				newest = newest.plus(1, ChronoUnit.MILLIS);
				ChatServer.log("Final newest: " + newest);
				Headers headers = exchange.getResponseHeaders();
				String lastModifiedString = newest.format(httpDateFormatter);
				headers.add("Last-Modified", lastModifiedString);
				ChatServer.log("Added Last-Modified header to response");
			} else {
				ChatServer.log("Did not put Last-Modified header in response");
			}
			byte [] bytes;
			if (ChatServer.version >= 3) {
				bytes = responseMessages.toString().getBytes("UTF-8");
			} else {
				String responseBody = "";
				for (String msg : plainList) {
					responseBody += msg + "\n";
				}
				bytes = responseBody.getBytes("UTF-8");
			}
			exchange.sendResponseHeaders(code, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
		return code;
	}
	
}
