package oy.tol.chatserver;

import java.nio.charset.Charset;
import java.sql.SQLException;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {
	
	public static final int MIN_USERNAME_LENGTH = 2;
	private static final int MIN_PASSWORD_LENGTH = 8;
	private static final int MIN_EMAIL_LENGTH = 4;

	ChatAuthenticator() {
		super("chat");
	}
	
	public boolean addUser(User user) throws SQLException {
		if (user.getName().trim().length() >= MIN_USERNAME_LENGTH && 
			user.getPassword().trim().length() >= MIN_PASSWORD_LENGTH &&
			user.getEmail().trim().length() >= MIN_EMAIL_LENGTH) {
				if (Charset.forName("US-ASCII").newEncoder().canEncode(user.getName())) {
					return ChatDatabase.getInstance().addUser(user);
				}	
		}
		return false;
	}

	@Override
	public boolean checkCredentials(String username, String password) {
		return ChatDatabase.getInstance().isRegisteredUser(username, password);
	}

}
