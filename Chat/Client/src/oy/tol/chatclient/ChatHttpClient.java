package oy.tol.chatclient;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

class ChatHttpClient {
	
	private static final String CHAT = "chat";
	private static final String REGISTRATION = "registration";

	private String host;

	private List<String> newMessages = null;
	private String serverNotification = "";
	
	public String getHost() {
		return host;
	}
	
	public void setHost(String newHost) {
		host = newHost;
		if (!host.endsWith("/")) {
			host += "/";
		}
	}
	
	public String getServerNotification() {
		return serverNotification;
	}
	
	public List<String> getNewMessages() {
		return newMessages;
	}
	
	public int getChatMessages(String username, String password) throws Exception {
		URL url = new URL(host + CHAT);
		
		HttpsURLConnection connection = createTrustingConnectionDebug(url);
		
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Content-Type", "text/plain");
		
		String auth = username + ":" + password;
		byte [] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
		String authHeaderValue = "Basic " + new String(encodedAuth);
		connection.setRequestProperty("Authorization", authHeaderValue);
		
		int responseCode = connection.getResponseCode();
		if (responseCode >= 200 && responseCode < 300) {
			newMessages = new ArrayList<String>();
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				newMessages.add(inputLine);
			}
			in.close();
			serverNotification = "";
		} else {
			newMessages = null;
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				serverNotification += " " + inputLine;
			}
			in.close();
		}		
		return responseCode;
	}
	
	public int postChatMessage(String username, String password, String message) throws Exception {
		URL url = new URL(host + CHAT);
		
		String auth = username + ":" + password;
		
		HttpsURLConnection connection = createTrustingConnectionDebug(url);
		
		byte [] msgBytes = message.getBytes(StandardCharsets.UTF_8);
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestProperty("Content-Type", "text/plain");
		connection.setRequestProperty("Content-Length", String.valueOf(msgBytes.length));		
		byte [] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
		String authHeaderValue = "Basic " + new String(encodedAuth);
		connection.setRequestProperty("Authorization", authHeaderValue);
		
		OutputStream writer = connection.getOutputStream();
		writer.write(msgBytes);
		writer.close();

		int responseCode = connection.getResponseCode();
		if (responseCode >= 200 && responseCode < 300) {
			// Successfully posted.
			serverNotification = "";
		} else {
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				serverNotification += " " + inputLine;
			}
			in.close();
		}		
		return responseCode;		
	}
	
	public int registerUser(String username, String password) throws Exception {
		URL url = new URL(host + REGISTRATION);

		String auth = username + ":" + password;
		
		HttpsURLConnection connection = createTrustingConnectionDebug(url);
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestProperty("Content-Type", "text/plain");
		connection.setRequestProperty("Content-Length", String.valueOf(auth.getBytes().length));		
		
		byte [] encodedData = auth.getBytes(StandardCharsets.UTF_8); 
		OutputStream writer = connection.getOutputStream();
		writer.write(encodedData);
		writer.close();
		
		int responseCode = connection.getResponseCode();
		if (responseCode >= 200 && responseCode < 300) {
			// Successfully registered.
			serverNotification = "";
		} else {
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				serverNotification += " " + inputLine;
			}
			in.close();
		}		
		return responseCode;
	}
	
	// For accepting self signed certificates. Not to be used in production software!
	
	private HttpsURLConnection createTrustingConnectionDebug(URL url) throws Exception {
		Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream("./localhost.cer"));

		KeyStore keyStore = KeyStore.getInstance("JKS");
		keyStore.load(null, null);
		keyStore.setCertificateEntry("localhost", certificate);

		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
		trustManagerFactory.init(keyStore);

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setSSLSocketFactory(sslContext.getSocketFactory());
		return connection;
	}
}
