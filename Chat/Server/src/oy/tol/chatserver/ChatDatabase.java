package oy.tol.chatserver;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.codec.digest.Crypt;

public class ChatDatabase {

	private Connection connection = null;
	private static ChatDatabase singleton = null;
	private SecureRandom secureRandom = null;

	public static synchronized ChatDatabase getInstance() {
		if (null == singleton) {
			singleton = new ChatDatabase();
		}
		return singleton;
	}

	private ChatDatabase() {	
		secureRandom = new SecureRandom();
	}

	public void open(String dbName) throws SQLException {
		boolean createDatabase = false;
		File file = new File(dbName);
		if (!file.exists() && !file.isDirectory()) {
			createDatabase = true;
		}
		String database = "jdbc:sqlite:" + dbName;
		connection = DriverManager.getConnection(database);
		if (createDatabase) {
			initializeDatabase();
		}
	}

	public void close() throws SQLException {
		if (null != connection) {
			connection.close();
			connection = null;
		}
	}

	public boolean addUser(String username, String password, String email) {
		boolean result = false;
		if (null != connection && !isUserNameRegistered(username)) {
			try {
				byte bytes[] = new byte[13];
				secureRandom.nextBytes(bytes);
				ChatServer.log("Random bytes: " + bytes);
				String saltBytes = new String(Base64.getEncoder().encode(bytes));
				String salt = "$6$" + saltBytes;
				ChatServer.log("Salt: " + salt);
				String hashedPassword = Crypt.crypt(password, salt);
				ChatServer.log("Hashed pw: " + hashedPassword);
				String insertUserString = "insert into users " +
						"VALUES('" + username + "','" + hashedPassword + "','" + email +"','" + salt + "')"; 
				Statement createStatement;
				createStatement = connection.createStatement();
				createStatement.executeUpdate(insertUserString);
				createStatement.close();
				result = true;
			} catch (SQLException e) {
				ChatServer.log("Could not register user in database: " + username);
				ChatServer.log("Reason: " + e.getErrorCode() + " " + e.getMessage());
			}
		}
		return result;
	}

	public boolean isUserNameRegistered(String username) {
		boolean result = false;
		if (null != connection) {
			try {
				String queryUser = "select name from users where name='" + username + "'";
				Statement queryStatement = connection.createStatement();
				ResultSet rs = queryStatement.executeQuery(queryUser);
				while (rs.next()) {
					String user = rs.getString("name");
					if (user.equals(username)) {
						result = true;
						break;
					}
				}
				queryStatement.close();
			} catch (SQLException e) {
				ChatServer.log("Could not check isUserNameRegistered: " + username);
				ChatServer.log("Reason: " + e.getErrorCode() + " " + e.getMessage());
			}

		}
		return result;
	}

	public boolean isRegisteredUser(String username, String password) {
		boolean result = false;
		Statement queryStatement = null;
		if (null != connection) {
			try {
				String queryUser = "select name, passwd, salt from users where name='" + username + "'";
				queryStatement = connection.createStatement();
				ResultSet rs = queryStatement.executeQuery(queryUser);
				while (rs.next()) {
					String user = rs.getString("name");
					String hashedPassword = rs.getString("passwd");
					if (user.equals(username)) { // should match since the SQL query...
					 	if (hashedPassword.equals(Crypt.crypt(password, hashedPassword))) {
							result = true;
							break;
						}
					}
				}
				queryStatement.close();
			} catch (SQLException e) {
				ChatServer.log("Could not check isRegisteredUser: " + username);
				ChatServer.log("Reason: " + e.getErrorCode() + " " + e.getMessage());
			}

		}
		return result;
	}

	public boolean insertMessage(String user, String nick, long dateTime, String message) {
		boolean result = false;
		try {
			String insertMsgStatement = "insert into messages " +
						"VALUES('" + user + "','" + nick + "','" + dateTime +"','" + message + "')"; 
			Statement createStatement;
			createStatement = connection.createStatement();
			createStatement.executeUpdate(insertMsgStatement);
			createStatement.close();
			result = true;
		} catch (SQLException e) {
			ChatServer.log("Could not insert new chat message from: " + user);
			ChatServer.log("Reason: " + e.getErrorCode() + " " + e.getMessage());
		}
		return result;
	}

	List<ChatMessage> getMessages(long since) {
		ArrayList<ChatMessage> messages = null;

		return messages;
	}

	private boolean initializeDatabase() throws SQLException {
		if (null != connection) {
			String createUsersString = "create table users " + 
					"(name varchar(32) NOT NULL, " +
					"passwd varchar(32) NOT NULL, " +
					"email varchar(32) NOT NULL, " +
					"salt varchar(32) NOT NULL, " +
					"PRIMARY KEY (name))";
			Statement createStatement = connection.createStatement();
			createStatement.executeUpdate(createUsersString);
			createStatement.close();
			createStatement = connection.createStatement();
			String createChatsString = "create table messages " +
					"(user varchar(32) NOT NULL, " +
					"nick varchar(32) NOT NULL" +
					"sent integer NOT NULL, " +
					"message varchar(1000) NOT NULL";
			createStatement.executeUpdate(createChatsString);
			createStatement.close();
			return true;
		}
		return false;
	}

}
