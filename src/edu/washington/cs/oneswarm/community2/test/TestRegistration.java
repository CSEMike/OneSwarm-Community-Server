package edu.washington.cs.oneswarm.community2.test;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.community2.shared.CommunityConstants;

public class TestRegistration {

	private KeyPair pair;

	public TestRegistration( KeyPair pair ) {
		this.pair = pair;
	}

	public void run() {
		try {

			Map<String, String> requestHeaders = new HashMap<String, String>();
			Map<String, String> formParams = new HashMap<String, String>();

			formParams.put(CommunityConstants.BASE64_PUBLIC_KEY, Base64.encode(pair.getPublic().getEncoded()));
			formParams.put(CommunityConstants.NICKNAME, "test user");

			URL url = new URL("http://127.0.0.1:8080/community");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setRequestMethod("POST");
			conn.setDoInput(true);
			conn.setDoOutput(true);

			for (String head : requestHeaders.keySet()) {
				conn.setRequestProperty(head, requestHeaders.get(head));
			}

			// add url form parameters
			OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());

			Iterator<String> params = formParams.keySet().iterator();
			while (params.hasNext()) {
				String name = params.next();
				String value = formParams.get(name);

				out.append(URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8"));
				if (params.hasNext()) {
					out.append("&");
				}
			}

			out.flush();

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line = null;
			while ((line = in.readLine()) != null) {
				System.out.println("resp line: " + line);
			}

			in.close();

			System.out.println("final status code: " + conn.getResponseCode() + " / " + conn.getResponseMessage());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		
		KeyPair pair = null; 
		try { 
			pair = (KeyPair) (new ObjectInputStream(new FileInputStream("/tmp/keys.scratch"))).readObject();
			System.out.println("loaded saved key pair");
		} catch( Exception e ) {
			CryptoUtils c = new CryptoUtils();
			pair = c.getPair();
			System.out.println(Base64.encode(pair.getPublic().getEncoded()));
			
			ObjectOutputStream saved = new ObjectOutputStream(new FileOutputStream("/tmp/keys.scratch"));
			saved.writeObject(pair);
			System.out.println("generated/saved key pair");
		}
		
		System.out.println("pub");
		System.out.println(Base64.encode(pair.getPublic().getEncoded()));
		
		(new TestRegistration(pair)).run();
	} 
}
