package oy.tol.chatserver;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.codec.digest.Crypt;

public class ChatDatabase {

	// TODO: Change from building queries as strings to prepared statements.
	private Connection connection = null;
	private static ChatDatabase singleton = null;
	private SecureRandom secureRandom = null;
	private static final int MAX_NUMBER_OF_RECORDS_TO_FETCH = 100;

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

	public void close() {
		if (null != connection) {
			try {
				connection.close();
			} catch (SQLException e) {
				ChatServer.log("*** ERROR in closing the database connection: " + e.getMessage());
				e.printStackTrace();
			}
			connection = null;
		}
	}

	public boolean addUser(User user) throws SQLException {
		boolean result = false;
		if (null != connection && !isUserNameRegistered(user.getName())) {
			long timestamp = System.currentTimeMillis();
			byte[] bytes = new byte[16];
			secureRandom.nextBytes(bytes);
			String salt = "$6$" + Base64.getEncoder().encodeToString(bytes);
			String hashedPassword = Crypt.crypt(user.getPassword(), salt);
			long duration = System.currentTimeMillis() - timestamp;
			ChatServer.log("Hashing and salting took " + duration + " ms");	
			String insertUser = "insert into users values (?, ?, ?)";
			PreparedStatement statement = connection.prepareStatement(insertUser);
			statement.setString(1, user.getName());
			statement.setString(2, hashedPassword);
			statement.setString(3, user.getEmail());
			statement.executeUpdate();
			statement.close();
			result = true;
		} else {
			ChatServer.log("User already registered: " + user.getName());
		}
		return result;
	}

	public boolean isUserNameRegistered(String username) {
		boolean result = false;
		if (null != connection) {
			try {
				String queryUser = "select name from users where name = ?";
				PreparedStatement queryStatement = connection.prepareStatement(queryUser);
				queryStatement.setString(1, username);
				ResultSet rs = queryStatement.executeQuery();
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
		PreparedStatement queryStatement = null;
		if (null != connection) {
			try {
				String queryUser = "select name, passwd from users where name = ?";
				queryStatement = connection.prepareStatement(queryUser);
				queryStatement.setString(1, username);
				ResultSet rs = queryStatement.executeQuery();
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
			if (!result) {
				ChatServer.log("Not a registered user!");
			}
		}
		return result;
	}

	public void insertMessage(String user, ChatMessage message) throws SQLException {
		String insertMsgStatement = "insert into messages values(?, ?, ?, ?)";
		PreparedStatement createStatement;
		createStatement = connection.prepareStatement(insertMsgStatement);
		createStatement.setString(1, user);
		createStatement.setString(2, message.nick);
		createStatement.setLong(3, message.dateAsLong());
		createStatement.setString(4, message.message);
		createStatement.executeUpdate();
		createStatement.close();
	}

	List<ChatMessage> getMessages(long since) throws SQLException {
		ArrayList<ChatMessage> messages = null;
		Statement queryStatement = null;

		String queryMessages = "select nick, sent, message from messages ";
		if (since > 0) {
			queryMessages += "where sent > " + since + " ";
		}
		queryMessages += " order by sent desc limit 100"; // limit 100";
		ChatServer.log(queryMessages);
		queryStatement = connection.createStatement();
		ResultSet rs = queryStatement.executeQuery(queryMessages);
		int recordCount = 0;
		while (rs.next() && recordCount < MAX_NUMBER_OF_RECORDS_TO_FETCH) {
			if (null == messages) {
				messages = new ArrayList<>();
			}
			String user = rs.getString("nick");
			String message = rs.getString("message");
			long sent = rs.getLong("sent");
			ChatMessage msg = new ChatMessage();
			msg.nick = user;
			msg.message = message;
			msg.setSent(sent);
			messages.add(msg);
			recordCount++;
		}
		queryStatement.close();
		return messages;
	}

	private boolean initializeDatabase() throws SQLException {
		if (null != connection) {
			String createUsersString = "create table users " + 
					"(name varchar(32) NOT NULL, " +
					"passwd varchar(32) NOT NULL, " +
					"email varchar(32) NOT NULL, " +
					"PRIMARY KEY (name))";
			Statement createStatement = connection.createStatement();
			createStatement.executeUpdate(createUsersString);
			createStatement.close();
			createStatement = connection.createStatement();
			String createChatsString = "create table messages " +
					"(user varchar(32) NOT NULL, " +
					"nick varchar(32) NOT NULL, " +
					"sent numeric NOT NULL, " +
					"message varchar(1000) NOT NULL," +
					"PRIMARY KEY(user,sent))";
			createStatement.executeUpdate(createChatsString);
			createStatement.close();
			return true;
		}
		return false;
	}

}
