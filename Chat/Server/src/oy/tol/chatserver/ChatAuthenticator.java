package oy.tol.chatserver;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {
	
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
