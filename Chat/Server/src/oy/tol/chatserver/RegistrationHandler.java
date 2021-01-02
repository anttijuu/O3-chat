package oy.tol.chatserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

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
			System.out.println("New user registration HTTP POST");
			Headers headers = exchange.getRequestHeaders();
			int contentLength = 0;
			String contentType = "";
			if (headers.containsKey("Content-Length")) {
				contentLength = Integer.parseInt(headers.get("Content-Length").get(0));
			}
			if (headers.containsKey("Content-Type")) {
				contentType = headers.get("Content-Type").get(0);
			}
			if (contentType.equalsIgnoreCase("text/plain")) {
				InputStream stream = exchange.getRequestBody();
				String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					        .lines()
					        .collect(Collectors.joining("\n"));
				stream.close();
				if (text.length() > 0) {
					String [] items = text.split(":");
					if (items.length == 3) {
						if (!authenticator.addUser(items[0], items[1], items[2])) {
							code = 403;
							messageBody = "Registration failed";
						} else {
							// Success
							exchange.sendResponseHeaders(code, -1);
							System.out.println("User registered successfully: " + items[0]);
						}
					} else {
						code = 400;
						messageBody = "No valid user:passwd combination in request body";
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
			System.out.println("*** Error in user registration: " + code + " " + messageBody);
			byte [] bytes = messageBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(code, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
	}
}
