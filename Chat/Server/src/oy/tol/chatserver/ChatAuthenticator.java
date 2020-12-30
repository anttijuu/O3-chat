package oy.tol.chatserver;

import java.util.Hashtable;
import java.util.Map;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {

	// TODO: It might be good for performance to cache users to the Map as did earlier.
	
	ChatAuthenticator() {
		super("chat");
	}
	
	public boolean addUser(String username, String password) {
		return ChatDatabase.instance().addUser(username, password);
	}

	@Override
	public boolean checkCredentials(String username, String password) {
		return ChatDatabase.instance().isUserRegistered(username, password);
	}
}
