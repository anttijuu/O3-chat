package oy.tol.chatserver;

import java.io.Console;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.sql.SQLException;
import java.time.LocalDateTime;
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

public class ChatServer {
	
	// TODO: read sensitive/contextual conf from config file (JSON/XML)
	// - db file with path, cert file, cert password, ...
	// TODO: use the same color output lib than in Client. ERRORS in red.
	// TODO: Next time, give the skeleton project to students to avoid hassle with tools.
	static int version = 2;
	private static boolean running = true;

	public static void main(String[] args) throws Exception {
		try {
			log("Launching ChatServer...");
			log("Initializing database...");
			if (args.length != 2) {
				log("Usage java -jar jar-file.jar dbname.db <version>");
				return;
			}
			int tmpVersion = Integer.parseInt(args[1]);
			if (tmpVersion >= 2 && tmpVersion <= 5) {
				version = tmpVersion;
			}
			ChatDatabase database = ChatDatabase.getInstance();
			database.open(args[0]);
			log("Initializing HttpServer with version " + version);
			HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
			log("Initializing SSL Context...");
			SSLContext sslContext = chatServerSSLContext();
			server.setHttpsConfigurator (new HttpsConfigurator(sslContext) {
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
			log("Initializing authenticator...");
			ChatAuthenticator authenticator = new ChatAuthenticator();
			log("Creating ChatHandler...");
			HttpContext chatContext = server.createContext("/chat", new ChatHandler());
			chatContext.setAuthenticator(authenticator);
			log("Creating RegistrationHandler...");
			server.createContext("/registration", new RegistrationHandler(authenticator));
			ExecutorService executor = Executors.newCachedThreadPool();
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
		} catch (Exception e) {
			e.printStackTrace();
		}
		log("Server finished, bye!");
	}

	private static SSLContext chatServerSSLContext() throws Exception {
		char[] passphrase = "s3rver-secr3t-d0no7-xp0s3".toCharArray();
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream("keystore.jks"), passphrase);

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
}
