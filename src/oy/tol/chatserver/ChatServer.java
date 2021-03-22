package oy.tol.chatserver;

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpServer;

public class ChatServer {
	
	// TODO: use the same color output lib than in Client. ERRORS in red.
	// TODO: Remember to change exercise material to reflect what is changed.
	// TODO: Next time, give the skeleton project to students to avoid hassle with tools.
	// TODO: Skeleton includes reading the properties file.
	// TODO: Change POSTs to return 204 since no data is returned
	// TODO: Change ChatMessage.dateAsInt to dateAsLong, int is confusing.
	// TODO: Should chat get return 304 Not Modified when If-Modified-Since returns nothing?
	// TODO: Client should use "Accept: application/json" header in GET /chat
	// TODO: All possible SQL queries should be prepared queries.
	// TODO: Include SQL query parameter sanitation: https://www.baeldung.com/sql-injection
	//       https://owasp.org/www-community/attacks/SQL_Injection
	// TODO: Implement 418 I'm a teapot (RFC 2324, RFC 7168) ;)
	// TODO: Check if this influences on cert usage:
	// https://stackoverflow.com/questions/26792813/why-do-i-get-no-name-matching-found-certificateexception

	private static boolean running = true;

	public static void main(String[] args) throws Exception {
		try {
			log("Launching ChatServer...");
			log("Initializing database...");
			if (args.length != 1) {
				log("Usage java -jar jar-file.jar config.properties");
				return;
			}
			readConfiguration(args[0]);
			ChatDatabase database = ChatDatabase.getInstance();
			database.open(dbFile);
			log("Initializing HttpServer...");
			HttpServer server = null;
			if (useHttps) {
				HttpsServer tmpServer = HttpsServer.create(new InetSocketAddress(8001), 0);
				log("Initializing SSL Context...");
				SSLContext sslContext = chatServerSSLContext();
				tmpServer.setHttpsConfigurator (new HttpsConfigurator(sslContext) {
					@Override
					public void configure (HttpsParameters params) {
					// get the remote address if needed
					InetSocketAddress remote = params.getClientAddress();
					SSLContext c = getSSLContext();
					// get the default parameters
					SSLParameters sslparams = c.getDefaultSSLParameters();
					params.setSSLParameters(sslparams);
					// statement above could throw IAE if any params invalid.
					// eg. if app has a UI and parameters supplied by a user.
					}
				});
				server = tmpServer;
			} else {
				server = HttpServer.create(new InetSocketAddress(8001), 0);
			}
			log("Initializing authenticator...");
			ChatAuthenticator authenticator = new ChatAuthenticator();
			log("Creating ChatHandler...");
			HttpContext chatContext = server.createContext("/chat", new ChatHandler());
			chatContext.setAuthenticator(authenticator);
			log("Creating RegistrationHandler...");
			server.createContext("/registration", new RegistrationHandler(authenticator));
			ExecutorService executor = null;
			if (useHttpThreadPool) {
				executor = Executors.newCachedThreadPool();
			}
			server.setExecutor(executor);
			log("Starting ChatServer!");
			server.start();
			Console console = System.console();
			while (running) {
				String input = console.readLine();
				if (input.equalsIgnoreCase("/quit")) {
					running = false;
					log("Stopping ChatServer in 3 secs...");
					server.stop(3);
					database.close();
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		log("Server finished, bye!");
	}

	private static SSLContext chatServerSSLContext() throws Exception {
		char[] passphrase = certificatePassword.toCharArray();
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream(certificateFile), passphrase);

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(ks, passphrase);

		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(ks);

		SSLContext ssl = SSLContext.getInstance("TLS");
		ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		return ssl;
	}
	
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_BLACK = "\u001B[30m";
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_BLUE = "\u001B[34m";
	public static final String ANSI_PURPLE = "\u001B[35m";
	public static final String ANSI_CYAN = "\u001B[36m";
	public static final String ANSI_WHITE = "\u001B[37m";

	public static void log(String message) {
		System.out.println(ANSI_GREEN + LocalDateTime.now() + ANSI_RESET + " " + message);
	}

	public static String dbFile = "O3-chat.db";
	public static boolean useHttps = true;
	public static String contentFormat = "application/json";
	public static boolean useModifiedHeaders = true;
	public static boolean useHttpThreadPool = true;
	public static String certificateFile = "keystore.jks";
	public static String certificatePassword = "";

	private static void readConfiguration(String configFileName) throws FileNotFoundException, IOException {
		System.out.println("Using configuration: " + configFileName);
		File configFile = new File(configFileName);
		Properties config = new Properties();
		FileInputStream istream;
		istream = new FileInputStream(configFile);
		config.load(istream);
		dbFile = config.getProperty("database");
		if (config.getProperty("https", "true").equalsIgnoreCase("true")) {
			useHttps = true;
		} else {
			useHttps = false;
		}
		contentFormat = config.getProperty("format");
		if (config.getProperty("modified-headers", "true").equalsIgnoreCase("true")) {
			useModifiedHeaders = true;
		} else {
			useModifiedHeaders = false;
		}
		if (config.getProperty("http-threads", "true").equalsIgnoreCase("true")) {
			useHttpThreadPool = true;
		} else {
			useHttpThreadPool = false;
		}
		certificateFile = config.getProperty("certfile");
		certificatePassword = config.getProperty("certpass");
		istream.close();
		if (dbFile == null || 
			contentFormat == null || 
			certificateFile == null || 
			certificatePassword == null) {
		   throw new RuntimeException("ChatServer Properties file does not have properties set.");
		} else {
		   System.out.println("Database file: " + dbFile);
		   System.out.println("Use https: " + useHttps);
		   System.out.println("Certificate file: " + certificateFile);
		   System.out.println("Content format: " + contentFormat);
		   System.out.println("Use Modified-Since: " + useModifiedHeaders);
		   System.out.println("Use HTTP thread pool: " + useHttpThreadPool);
		}
	 }
}
