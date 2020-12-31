package oy.tol.chatclient;

import java.io.Console;
import java.io.IOException;
import java.util.List;

public class ChatClient {

	private static final String SERVER = "https://localhost:8001/";
	private static final String CMD_SERVER	 = "/server";
	private static final String CMD_REGISTER = "/register";
	private static final String CMD_LOGIN = "/login";
	private static final String CMD_GET = "/get";
	private static final String CMD_HELP = "/help";
	private static final String CMD_EXIT = "/exit";
	
	private String currentServer = SERVER;
	private String username = null;
	private String password = null;
	
	private ChatHttpClient httpClient = null;
	
	public static void main(String[] args) {
		ChatClient client = new ChatClient();
		client.run();
	}

	public void run() {
		httpClient = new ChatHttpClient();
		httpClient.setHost(currentServer);
		printCommands();
		System.out.println("Using server " + currentServer);
		Console console = System.console();
		if (null == username) {
			System.out.println("!! Register or login to server first.");
		}
		boolean running = true;
		while (running) {
			System.out.print("O3-chat > ");
			String command = console.readLine();
			switch (command) {
			case CMD_SERVER:
				changeServer(console);
				break;
			case CMD_REGISTER:
				registerUser(console);
				break;
			case CMD_LOGIN:
				getUserCredentials(console);
				break;
			case CMD_GET:
				getNewMessages();
				break;
			case CMD_HELP:
				printCommands();
				break;
			case CMD_EXIT:
				running = false;
				break;
			default:
				if (command.length() > 0) {
					postMessage(command);
				}
				break;
			}			
		}
		System.out.println("Bye!");
	}
	
	private void changeServer(Console console) {
		System.out.print("Enter server address > ");
		String newServer = console.readLine();
		if (newServer.length() > 0) {
			System.out.print("Change server from " + currentServer + " to " + newServer + "Y/n? > ");
			String confirmation = console.readLine();
			if (confirmation.length() == 0 || confirmation.equalsIgnoreCase("Y")) {
				currentServer = newServer;
				httpClient.setHost(currentServer);
				username = null;
				password = null;
				System.out.println("Remember to register and/or login to the new server!");
			}
		}
		System.out.println("Server in use is " + currentServer);
	}
	
	/**
	 * Get user credentials from console .
	 */
	private void getUserCredentials(Console console) {
		System.out.print("Enter username > ");
		String newUsername = console.readLine();
		if (newUsername.length() > 0) {
			username = newUsername;
		}
		System.out.print("Enter password > ");
		char [] newPassword = console.readPassword();
		if (newPassword.length > 0) {
			password = new String(newPassword);
		}
	}
	
	private void registerUser(Console console) {
		System.out.println("Give user credentials for new user for server " + currentServer);
		getUserCredentials(console);
		try {
			int response = httpClient.registerUser(username, password);
			if (response >= 200 || response < 300) {
				System.out.println("Registered successfully, you may start chatting!");
			} else {
				System.out.println("Failed to register!");
				System.out.println("Error from server: " + response + " " + httpClient.getServerNotification());
			}
		} catch (Exception e) {
			System.out.println("ERROR in user registration on server " + currentServer);
			System.out.println(e.getLocalizedMessage());
		}
	}
	
	private void getNewMessages() {
		try {
			if (null != username) {
				int response = httpClient.getChatMessages(username, password);		
				if (response >= 200 || response < 300) {
					List<String> messages = httpClient.getNewMessages();
					if (null != messages) {
						for (String message : messages) {
							System.out.println(message);
						}
					} else {
						System.out.println("No new messages from server.");
					}
				} else {
					System.out.println("Error from server: " + response + " " + httpClient.getServerNotification());
				}
			} else {
				System.out.println("Not yet registered or logged in!");
			}
		} catch (Exception e) {
			System.out.println("ERROR in getting messages from server " + currentServer);
			System.out.println(e.getLocalizedMessage());
		}
	}
	
	private void postMessage(String message) {
		if (null != username) {
			try {
				int response = httpClient.postChatMessage(username, password, message);
				if (response < 200 || response >= 300) {
					System.out.println("Error from server: " + response + " " + httpClient.getServerNotification());
				}
			} catch (Exception e) {
				System.out.println("ERROR in posting message to server " + currentServer);
				System.out.println(e.getLocalizedMessage());
			}
		} else {
			System.out.println("Must register/login to server before posting messages!");
		}
	}
	
	private void printCommands() {
		System.out.println("--- O3 Chat Client Commands ---");
		System.out.println("/server -- Change the server");
		System.out.println("/register  -- Register as a new user in server");
		System.out.println("/login -- Login using already registered credentials");
		System.out.println("/get -- Get new messages from server");
		System.out.println("/help -- Prints out this information");
		System.out.println("/exit -- Exit the client app");
		System.out.println("send a message to the chat server");
	}
	
	
}
