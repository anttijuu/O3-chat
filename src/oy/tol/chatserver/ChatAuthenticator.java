package oy.tol.chatserver;

import java.nio.charset.Charset;
import java.sql.SQLException;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {
	
	private static final int MIN_USERNAME_LENGTH = 2;
	private static final int MIN_PASSWORD_LENGTH = 8;
	private static final int MIN_EMAIL_LENGTH = 4;

	ChatAuthenticator() {
		super("chat");
	}
	
	public boolean addUser(String username, String password, String email) throws SQLException {
		if (username.trim().length() >= MIN_USERNAME_LENGTH && 
			password.trim().length() >= MIN_PASSWORD_LENGTH &&
			email.trim().length() >= MIN_EMAIL_LENGTH) {
				if (Charset.forName("US-ASCII").newEncoder().canEncode(username)) {
					return ChatDatabase.getInstance().addUser(username, password, email);
				}	
		}
		return false;
	}

	@Override
	public boolean checkCredentials(String username, String password) {
		return ChatDatabase.getInstance().isRegisteredUser(username, password);
	}


}
