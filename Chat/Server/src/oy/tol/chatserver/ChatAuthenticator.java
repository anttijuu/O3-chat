package oy.tol.chatserver;

import java.util.Hashtable;
import java.util.Map;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {

	// TODO: It might be good for performance to cache users to the memory resident Map as did earlier.
	
	ChatAuthenticator() {
		super("chat");
	}
	
	public boolean addUser(String username, String password, String email) {
		return ChatDatabase.getInstance().addUser(username, password, email);
	}

	@Override
	public boolean checkCredentials(String username, String password) {
		return ChatDatabase.getInstance().isRegisteredUser(username, password);
	}
}
