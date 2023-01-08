package oy.tol.chatserver;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import com.sun.net.httpserver.BasicAuthenticator;

public class ChatAuthenticator extends BasicAuthenticator {
	
	public static final int MIN_USERNAME_LENGTH = 2;
	public static final int MAX_USERNAME_LENGTH = 20;
	private static final int MIN_PASSWORD_LENGTH = 8;
	private static final int MIN_EMAIL_LENGTH = 4;
	private static final int MAX_EMAIL_LENGTH = 256;

	ChatAuthenticator() {
		super("chat");
	}
	
	public boolean addUser(User user) throws SQLException {
		if (user.getName().length() >= MIN_USERNAME_LENGTH && 
			 user.getName().length() <= MAX_USERNAME_LENGTH && 
			 user.getPassword().length() >= MIN_PASSWORD_LENGTH &&
			 user.getEmail().length() >= MIN_EMAIL_LENGTH && 
			 user.getEmail().length() <= MAX_EMAIL_LENGTH) {
				if (StandardCharsets.US_ASCII.newEncoder().canEncode(user.getName())) {
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
