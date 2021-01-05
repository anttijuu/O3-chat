package oy.tol.chatserver;

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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

public class ChatServer {
	
	public static void main(String[] args) throws Exception {
		try {
			System.out.println("Launching ChatServer");
			System.out.println("Initializing database..");
			ChatDatabase database = ChatDatabase.getInstance();
			database.open("/Users/juustila/workspace/O3/Chat/Server/O3-chat.db");
			System.out.println("Initializing HttpServer..");
			HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
			SSLContext sslContext = chatServerSSLContext();
			server.setHttpsConfigurator (new HttpsConfigurator(sslContext) {
		        public void configure (HttpsParameters params) {
		        // get the remote address if needed
		        InetSocketAddress remote = params.getClientAddress();
		        SSLContext c = getSSLContext();
		        // get the default parameters
		        SSLParameters sslparams = c.getDefaultSSLParameters();
//		        if (remote.equals (...) ) {
//		            // modify the default set for client x
//		        }
		        params.setSSLParameters(sslparams);
		        // statement above could throw IAE if any params invalid.
		        // eg. if app has a UI and parameters supplied by a user.
		        }
		    });
			System.out.println("Initializing authenticator...");
			ChatAuthenticator authenticator = new ChatAuthenticator();
			System.out.println("Creating ChatHandler.");
			HttpContext chatContext = server.createContext("/chat", new ChatHandler());
			chatContext.setAuthenticator(authenticator);
			System.out.println("Creating RegistrationHandler.");
			server.createContext("/registration", new RegistrationHandler(authenticator));
			server.setExecutor(null);
			System.out.println("Starting HttpServer.");
			server.start();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
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
}
