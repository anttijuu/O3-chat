package oy.tol.chatserver;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ChatDatabase {

	private Connection connection = null;
	private static ChatDatabase singleton = null;

	public static ChatDatabase instance() {
		if (null == singleton) {
			singleton = new ChatDatabase();
		}
		return singleton;
	}

	private ChatDatabase() {	
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
		// TODO: Save hashed password only!
		boolean result = false;
		if (null != connection && !isUserNameRegistered(username)) {
			try {
				String insertUserString = "insert into users " +
						"VALUES('" + username + "','" + password + "','" + email +"')"; 
				Statement createStatement;
				createStatement = connection.createStatement();
				createStatement.executeUpdate(insertUserString);
				result = true;
			} catch (SQLException e) {
				System.out.println("Could not register user in database: " + username);
				System.out.println("Reason: " + e.getErrorCode() + " " + e.getMessage());
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
			} catch (SQLException e) {
				System.out.println("Could not check isUserNameRegistered: " + username);
				System.out.println("Reason: " + e.getErrorCode() + " " + e.getMessage());
			}

		}
		return result;
	}

	public boolean isRegisteredUser(String username, String password) {
		boolean result = false;
		if (null != connection) {
			try {
				String queryUser = "select name, passwd from users where name='" + username + "'";
				Statement queryStatement = connection.createStatement();
				ResultSet rs = queryStatement.executeQuery(queryUser);
				while (rs.next()) {
					String user = rs.getString("name");
					String pw = rs.getString("passwd");
					if (user.equals(username) && pw.equals(password)) {
						result = true;
						break;
					}
				}
			} catch (SQLException e) {
				System.out.println("Could not check isRegisteredUser: " + username);
				System.out.println("Reason: " + e.getErrorCode() + " " + e.getMessage());
			}

		}
		return result;
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
			return true;
		}
		return false;
	}

}
