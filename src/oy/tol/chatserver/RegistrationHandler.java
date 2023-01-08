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
		String responseBody = "";
		
		try {
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
				if (contentLength > 0) {
					String expectedContentType = ChatServer.contentFormat;
					if (contentType.equalsIgnoreCase(expectedContentType)) {
						InputStream stream = exchange.getRequestBody();
						String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
								.collect(Collectors.joining("\n"));
						stream.close();
						if (ChatServer.contentFormat.equals("application/json")) {
							JSONObject registrationMsg = new JSONObject(text);
							String username = registrationMsg.getString("username").trim();
							String password = registrationMsg.getString("password").trim();
							String email = registrationMsg.getString("email").trim();
							User newUser = new User(username, password, email);
							if (!authenticator.addUser(newUser)) {
								code = 400;
								responseBody = "Registration failed";
							} else {
								// Success
								exchange.sendResponseHeaders(code, -1);
								ChatServer.log("User registered successfully: " + username);
							}
						} else {
							String [] items = text.split(":");
							if (items.length == 2) {
								User newUser = new User(items[0], items[1], "dummy@email.com");
								if (!authenticator.addUser(newUser)) {
									code = 400;
									responseBody = "Registration failed";
								} else {
									// Success
									exchange.sendResponseHeaders(code, -1);
									ChatServer.log("User registered successfully: " + items[0]);
								}								
							} else {
								code = 400;
								responseBody = "No valid registration data in request body";									
							}
						}
					} else {
						code = 411;
						responseBody = "Content-Type must be " + expectedContentType;
					}
				} else {
					code = 411;
					responseBody = "No content length in request.";
				}
			} else {
				code = 405;
				responseBody = "Method not supported.";
			}
		} catch (JSONException e) {
			code = 400;
			responseBody = "No valid registration data in request body";
		} catch (Exception e) {
			code = 500;
			responseBody = "Server internal error";
			ChatServer.log("Failed to register the user: " + e.getMessage());
		}
		if (code >= 400) {
			ChatServer.log("*** Error in user /registration: " + code + " " + responseBody);
			byte [] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(code, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
	}
}
