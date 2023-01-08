package oy.tol.chatserver;

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
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
	// TODO: Change POSTs to return 204 since no data is returned
	// TODO: Should chat get return 304 Not Modified when If-Modified-Since returns nothing?
	// TODO: Client should use "Accept: application/json" header in GET /chat
	// TODO: Check & apply: https://anantjain60.medium.com/secure-coding-techniques-in-java-9b81901beea8
	// TODO: Include SQL query parameter sanitation: https://www.baeldung.com/sql-injection
	//       https://owasp.org/www-community/attacks/SQL_Injection
	// TODO: Implement 418 I'm a teapot (RFC 2324, RFC 7168) ;)
	// TODO: Check if this influences on cert usage:
	// https://stackoverflow.com/questions/26792813/why-do-i-get-no-name-matching-found-certificateexception
	// TODO: get real certificate instead of using self-signed one.

	private static boolean running = true;

	public static void main(String[] args) throws Exception {
		try {
			log("Launching ChatServer...");
			if (args.length != 2) {
				log("Usage java -jar jar-file.jar config.properties certpassword");
				return;
			}
			certificatePassword = args[1];
			log("Reading configuration...");
			readConfiguration(args[0]);
			log("Initializing database...");
			ChatDatabase database = ChatDatabase.getInstance();
			database.open(dbFile);
			log("Initializing HttpServer...");
			HttpServer server = null;
			if (useHttps) {
				HttpsServer tmpServer = HttpsServer.create(new InetSocketAddress(serverPort), 0);
				log("Initializing SSL Context...");
				SSLContext sslContext = chatServerSSLContext();
				tmpServer.setHttpsConfigurator (new HttpsConfigurator(sslContext) {
					@Override
					public void configure (HttpsParameters params) {
						try {
							// get the remote address if needed
							InetSocketAddress remote = params.getClientAddress();
							SSLContext c = getSSLContext();
							// get the default parameters
							SSLParameters sslparams = c.getDefaultSSLParameters();
							params.setSSLParameters(sslparams);
							// statement above could throw IAE if any params invalid.
							// eg. if app has a UI and parameters supplied by a user.
						} catch (Exception e) {
							System.out.println("Exception in HttpsConfigurator.configure: " + e.getMessage());
						}
					}
				});
				server = tmpServer;
			} else {
				log("Using http, not https");
				server = HttpServer.create(new InetSocketAddress(serverPort), 0);
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
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e) {
			log("Something wrong with the certificate!", ANSI_RED);
			e.printStackTrace();
		}
		log("Server finished, bye!");
	}

	private static SSLContext chatServerSSLContext() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException {
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

	public static void log(String message, String color) {
		System.out.println(color + LocalDateTime.now() + ANSI_RESET + " " + message);
	}

	static String dbFile = "O3-chat.db";
	static int serverPort = 10000;
	static boolean useHttps = true;
	static String contentFormat = "application/json";
	static boolean useModifiedHeaders = true;
	static boolean useHttpThreadPool = true;
	static String certificateFile = "keystore.jks";
	static String certificatePassword = "";

	private static void readConfiguration(String configFileName) throws FileNotFoundException, IOException {
		log("Using configuration: " + configFileName, ANSI_YELLOW);
		File configFile = new File(configFileName);
		Properties config = new Properties();
		FileInputStream istream;
		istream = new FileInputStream(configFile);
		config.load(istream);
		serverPort = Integer.valueOf(config.getProperty("port", "10000"));
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
		istream.close();
		if (dbFile == null || 
			contentFormat == null || 
			certificateFile == null) {
		   throw new RuntimeException("ChatServer Properties file does not have properties set.");
		} else {
			log("Server port: " + serverPort, ANSI_YELLOW);
		   log("Database file: " + dbFile, ANSI_YELLOW);
		   log("Use https: " + useHttps, ANSI_YELLOW);
		   log("Certificate file: " + certificateFile, ANSI_YELLOW);
		   log("Content format: " + contentFormat, ANSI_YELLOW);
		   log("Use Modified-Since: " + useModifiedHeaders, ANSI_YELLOW);
		   log("Use HTTP thread pool: " + useHttpThreadPool, ANSI_YELLOW);
		}
	 }
}
