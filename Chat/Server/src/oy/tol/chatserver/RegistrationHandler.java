package oy.tol.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class RegistrationHandler implements HttpHandler {

	private ChatAuthenticator authenticator = null;
	
	RegistrationHandler(ChatAuthenticator authenticator) {
		this.authenticator = authenticator;
	}
	
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		int code = 200;
		String messageBody = "";
		
		if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
			ChatServer.log("New user registration HTTP POST");
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
				String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					        .lines()
					        .collect(Collectors.joining("\n"));
				stream.close();
				if (text.length() > 0) {
					try {
						JSONObject registrationMsg = new JSONObject(text);
						String username = registrationMsg.getString("username");
						String password = registrationMsg.getString("password");
						String email = registrationMsg.getString("email");						
						if (!authenticator.addUser(username, password, email)) {
							code = 403;
							messageBody = "Registration failed";
						} else {
							// Success
							exchange.sendResponseHeaders(code, -1);
							ChatServer.log("User registered successfully: " + username);
						}
					} catch (JSONException e) {
						code = 400;
						messageBody = "No valid registration data in request body";
					}
				} else {
					code = 400;
					messageBody = "No content in request";
				}
			} else {
				code = 411;
				messageBody = "Content-Type must be text/plain.";
			}
		} else {
			code = 400;
			messageBody = "Not supported.";
		}
		if (code < 200 || code > 299) {
			ChatServer.log("*** Error in user registration: " + code + " " + messageBody);
			byte [] bytes = messageBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(code, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
	}
}
