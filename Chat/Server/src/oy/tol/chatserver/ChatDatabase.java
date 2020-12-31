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
		}
	}

	public boolean addUser(String username, String password) {
		// TODO: Save hashed password only!
		boolean result = false;
		if (null != connection && !isRegisteredUser(username, password)) {
			try {
				String insertUserString = "insert into users " +
						"VALUES('" + username + "','" + password + "')"; 
				Statement createStatement;
				createStatement = connection.createStatement();
				createStatement.executeUpdate(insertUserString);
				result = true;
			} catch (SQLException e) {
				e.printStackTrace();
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
				e.printStackTrace();
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
				e.printStackTrace();
			}

		}
		return result;
	}

	private boolean initializeDatabase() throws SQLException {
		if (null != connection) {
			String createUsersString = "create table users " + 
					"(name varchar(32) NOT NULL, " +
					"passwd varchar(32) NOT NULL, " +
					"PRIMARY KEY (name))";
			Statement createStatement = connection.createStatement();
			createStatement.executeUpdate(createUsersString);
			return true;
		}
		return false;
	}

}
