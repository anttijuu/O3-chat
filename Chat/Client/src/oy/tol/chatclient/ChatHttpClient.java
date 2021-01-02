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
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONObject;

class ChatHttpClient {
	
	private static final String CHAT = "chat";
	private static final String REGISTRATION = "registration";

	private List<String> newMessages = null;
	private String serverNotification = "";
	
	private ChatClientDataProvider dataProvider = null;
	
	ChatHttpClient(ChatClientDataProvider provider) {
		dataProvider = provider;
	}
		
	public String getServerNotification() {
		return serverNotification;
	}
	
	public List<String> getNewMessages() {
		return newMessages;
	}
	
	public int getChatMessages() throws Exception {
		String addr = dataProvider.getServer();
		if (!addr.endsWith("/")) {
			addr += "/";
		}
		addr += CHAT;
		URL url = new URL(addr);
		
		HttpsURLConnection connection = createTrustingConnectionDebug(url);
		
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Content-Type", "application/json");
		
		String auth = dataProvider.getUsername() + ":" + dataProvider.getPassword();
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
	
	public int postChatMessage(String message) throws Exception {
		String addr = dataProvider.getServer();
		if (!addr.endsWith("/")) {
			addr += "/";
		}
		addr += CHAT;
		URL url = new URL(addr);
		
		String auth = dataProvider.getUsername() + ":" + dataProvider.getPassword();
		
		HttpsURLConnection connection = createTrustingConnectionDebug(url);
		
		JSONObject msg = new JSONObject();
		msg.put("user", dataProvider.getNick());
		msg.put("message", message);
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
		String dateText = now.format(formatter);
		msg.put("sent", dateText);
		
		byte [] msgBytes = msg.toString().getBytes(StandardCharsets.UTF_8); 
		
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestProperty("Content-Type", "application/json");
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
	
	public int registerUser() throws Exception {
		String addr = dataProvider.getServer();
		if (!addr.endsWith("/")) {
			addr += "/";
		}
		addr += REGISTRATION;
		URL url = new URL(addr);

		String auth = dataProvider.getUsername() + ":" + dataProvider.getPassword()+ ":" + dataProvider.getEmail();
		
		HttpsURLConnection connection = createTrustingConnectionDebug(url);
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestProperty("Content-Type", "text/plain");
		byte [] encodedData = auth.getBytes(StandardCharsets.UTF_8); 
		connection.setRequestProperty("Content-Length", String.valueOf(encodedData.length));		
		
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
