package oy.tol.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
	
	private static final DateTimeFormatter jsonDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
	private static final DateTimeFormatter httpDateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss.SSS z", Locale.ENGLISH).withZone(ZoneId.of("GMT"));
	// TODO: Think what would be a suitable max msg len. Also put this into settings file.
	private static final int MAX_CHAT_JSON_MESSAGE_LENGTH = 8192;

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		Result result = new Result();
		result.code = 200;
		try {
			if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
				result = handleChatMessageFromClient(exchange);
			} else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
				result = handleGetRequestFromClient(exchange);
			} else {
				result.code = 400;
				result.response = "Not supported.";
			}
		} catch (JSONException e) {
			result.code = 400;
			result.response = "🤬 Invalid JSON in request: " + e.getMessage();
		} catch (SQLException e) {
			String msg = e.getMessage();
			if (msg.contains("SQLITE_CONSTRAINT_PRIMARYKEY")) {
				result.code = 429;
				result.response = "🤬 Slow down chatting or your requests will be limited or IP banned!";
			} else {
				result.code = 500;
				result.response = "Database error in saving chat message: " + e.getMessage();
			}
		} catch (IOException e) {
			result.code = 500;
			result.response = "Error in handling the request: " + e.getMessage();
		} catch (DateTimeParseException e) {
			result.code = 400;
			result.response = "Error in parsing dates in the request: " + e.getMessage();
		} catch (Exception e) {
			result.code = 500;
			result.response = "Server error: " + e.getMessage();
		}
		if (result.code >= 400) {
			ChatServer.log("*** Error in /chat: " + result.code + " " + result.response);
			byte [] bytes = result.response.getBytes("UTF-8");
			exchange.sendResponseHeaders(result.code, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
	}
	
	private Result handleChatMessageFromClient(HttpExchange exchange) throws Exception {
		Result result = new Result();
		result.code = 200;
		Headers headers = exchange.getRequestHeaders();
		int contentLength = 0;
		String contentType = "";
		if (headers.containsKey("Content-Length")) {
			contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
			if (contentLength > MAX_CHAT_JSON_MESSAGE_LENGTH) {
				result.code = 413;
				result.response = "Content too large";
				return result;
			}
		} else {
			result.code = 411;
			result.response = "No content length in request.";
			return result;
		}
		if (headers.containsKey("Content-Type")) {
			contentType = headers.get("Content-Type").get(0);
		} else {
			result.code = 400;
			result.response = "No content type in request.";
			return result;
		}
		String user = exchange.getPrincipal().getUsername();
		String expectedContentType = ChatServer.contentFormat;
		if (contentType.equalsIgnoreCase(expectedContentType)) {
			InputStream stream = exchange.getRequestBody();
			String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				        .lines()
				        .collect(Collectors.joining("\n"));
			ChatServer.log("Got chat message to ChatHandler thread id " + Thread.currentThread().getId());
			stream.close();
			if (text.trim().length() > 0) {
				processMessage(user, text);
				exchange.sendResponseHeaders(result.code, -1);
				ChatServer.log("New chatmessage saved");
			} else {
				result.code = 400;
				result.response = "No content in request.";
	
				ChatServer.log(result.response);
			}
		} else {
			result.code = 411;
			result.response = "Content-Type must be application/json.";
			ChatServer.log(result.response);
		}
		return result;
	}
	
	private void processMessage(String user, String text) throws JSONException, SQLException {
		if (ChatServer.contentFormat.equals("application/json")) {
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
			LocalDateTime now = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
			newMessage.sent = now;
			ChatDatabase.getInstance().insertMessage(user, newMessage);
		}
	}
	
	private Result handleGetRequestFromClient(HttpExchange exchange) throws IOException, SQLException {
		Result result = new Result();
		result.code = 200;
		result.response = "";
		
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
			result.code = 204;
			exchange.sendResponseHeaders(result.code, -1);			
			return result;
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
				if (ChatServer.contentFormat.equals("application/json")) {
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
		if (ChatServer.contentFormat.equals("application/json")) {
			if (responseMessages.isEmpty()) {
				isEmpty = true;
			}
		} else if (null == plainList) {
			isEmpty = true;
		}
		if (isEmpty) {
			ChatServer.log("No new messages to deliver to client since last request");
			result.code = 204;
			exchange.sendResponseHeaders(result.code, -1);
		} else {
			ChatServer.log("Delivering " + responseMessages.length() + " messages to client");
			Headers headers = exchange.getResponseHeaders();
			headers.add("Content-Type", ChatServer.contentFormat);
			if (null != newest && ChatServer.useModifiedHeaders) {
				newest = newest.plus(1, ChronoUnit.MILLIS);
				ChatServer.log("Final newest: " + newest);
				String lastModifiedString = newest.format(httpDateFormatter);
				headers.add("Last-Modified", lastModifiedString);
				ChatServer.log("Added Last-Modified header to response");
			} else {
				ChatServer.log("Did not put Last-Modified header in response");
			}
			byte [] bytes;
			if (ChatServer.contentFormat.equals("application/json")) {
				bytes = responseMessages.toString().getBytes("UTF-8");
			} else {
				String responseBody = "";
				for (String msg : plainList) {
					responseBody += msg + "\n";
				}
				bytes = responseBody.getBytes("UTF-8");
			}
			exchange.sendResponseHeaders(result.code, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
		return result;
	}
	
}
