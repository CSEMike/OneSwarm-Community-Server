package edu.washington.cs.oneswarm.community2.test;


import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyPair;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.community2.shared.CommunityConstants;
import edu.washington.cs.oneswarm.community2.utils.ByteManip;


public class TestEmbeddedServer {
	
	private int port;
	private String host;
	private String community_url;

	List<KeyPair> generatedKeys = new LinkedList<KeyPair>();
	private ExecutorService threadPool;
	
	public TestEmbeddedServer( String host, int port ) {
		this.host = host;
		this.port = port;
		
		this.community_url = host + ":" + port + "/community";
		
		getKeys();
		
		threadPool = Executors.newFixedThreadPool(50);
	}
	
	private void getKeys() {
		try {
			generatedKeys = (List<KeyPair>)((new ObjectInputStream(new FileInputStream("/tmp/scratch_keys"))).readObject());
		} catch( Exception e ) {
			System.err.println("couldn't get scratch keys: " + e.toString());
			System.out.println("generating keys...");
			
			CryptoUtils c = new CryptoUtils();
			generatedKeys = new LinkedList<KeyPair>();
			for( int i=0; i<10000; i++ ) {
				if( (i%100) == 0 ) {
					System.out.println("done " + i);
				}
				
				generatedKeys.add(c.getPair());
			}
			System.out.println("done, writing...");
			try {
				(new ObjectOutputStream(new FileOutputStream("/tmp/scratch_keys"))).writeObject(generatedKeys);
			} catch (Exception e2 ) {
				e.printStackTrace();
			}
			System.out.println("done");
		}
	}
	
	public void doit() {
		
//		mixed_bench();
//		try_unicode();
		
	}
	
	private void try_unicode() { 
		
		KeyPair pair = generatedKeys.remove(0);
		threadPool.submit(new RegistrationRequest(pair, "ŽfadfdafŒaŸber"));
		
		try {
			Thread.sleep(5000);
		} catch( Exception e ) {}
		
	}
	
	private void register_all() {
		for( KeyPair p : generatedKeys ) {
			threadPool.submit(new RegistrationRequest(p));
		}
		
		while( registered.size() < generatedKeys.size() ) {
			try {
				Thread.sleep(5*1000);
			} catch( Exception e ) {}
			System.out.println("registered: " + registered.size());
		}
	}
	
	final List<KeyPair> registered = Collections.synchronizedList(new ArrayList<KeyPair>());
	final Set<String> recently_challenged = Collections.synchronizedSet(new HashSet<String>());
	
	private void mixed_bench() {
		
		Random r = new Random();
		
		Collections.shuffle(generatedKeys);
		int thresh = generatedKeys.size();
		
		long start = System.currentTimeMillis();
		
		int registrations=0, requests=0, deletes=0;
		while( (System.currentTimeMillis()-start) < 60 * 1000 ) {
			double flip = r.nextDouble();
			if( flip < 0.10 ) {
				// add/register
				if( generatedKeys.size() > 0 ) {
					KeyPair pair = generatedKeys.remove(0);
					threadPool.submit(new RegistrationRequest(pair));
					registrations++;
				}
			} else if( flip < 0.11 ) { 
				// remove
				if( registered.size() == 0 ) {
					continue;
				}
				
				KeyPair del = null;
				synchronized(registered) {
					do {
						del = registered.get(r.nextInt(registered.size()));
					} while( recently_challenged.contains(CryptoUtils.getBase64FromKey(del.getPublic())) );
				}
				
				threadPool.submit(new DeregistrationRequest(CryptoUtils.getBase64FromKey(del.getPublic())));
				deletes++;
			} else {
				// request 
				if( registered.size() == 0 ) {
					continue;
				}
				
				KeyPair req = null;
				synchronized(registered) {
					do {
						req = registered.get(r.nextInt(registered.size()));
					} while( recently_challenged.contains(CryptoUtils.getBase64FromKey(req.getPublic())) );
				}
				recently_challenged.add(CryptoUtils.getBase64FromKey(req.getPublic()));
				threadPool.submit(new PeerRequest(req));
				requests++;
			}
			
			try {
				Thread.sleep(10);
			} catch( Exception e ) {}
		}
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.out.println("reg: " + registrations + " reqs: " + requests + " removes: " + deletes + " in " + (System.currentTimeMillis()-start));
	}
	
	// assumes this key is registered. 
	class PeerRequest implements Runnable {
		private KeyPair keys;
		private String base64Key;
		private long start;
		
		public PeerRequest( KeyPair keys ) {
			this.keys = keys;
			base64Key = CryptoUtils.getBase64FromKey(keys.getPublic());
		}
		
		public void run() {
			try {
				
				start = System.currentTimeMillis();
				
				String theURLString = community_url + "?" + CommunityConstants.BASE64_PUBLIC_KEY + "=" + URLEncoder.encode(base64Key, "UTF-8");
				
				URL url = new URL(theURLString);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(10*1000); // 10 second timeouts
				conn.setReadTimeout(10*1000);
				conn.setRequestMethod("GET");
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String l = in.readLine();
				
				if( l == null ) {
					throw new IOException("null challenge line");
				}

				if (l.startsWith(CommunityConstants.CHALLENGE)) {
					String[] toks = l.split("\\s+");
					if (toks.length != 2) {
						throw new IOException("Received a malformed challenge");
					}

					long challenge = Long.parseLong(toks[1]);
					reissueWithResponse(challenge);
				}
				
			} catch( Exception e ) {
				e.printStackTrace();
			} finally { 
				recently_challenged.remove(CryptoUtils.getBase64FromKey(keys.getPublic()));
			}
		}
		
		public void reissueWithResponse( long challenge ) {
			try {
				byte[] encrypted_response = null;

				Signature signer = Signature.getInstance("SHA1withRSA");
				signer.initSign(keys.getPrivate());
				signer.update(ByteManip.ltob(challenge+1));
				encrypted_response = signer.sign();

				String urlStr = community_url + "?" + CommunityConstants.BASE64_PUBLIC_KEY + "=" + URLEncoder.encode(base64Key, "UTF-8") + "&" + CommunityConstants.CHALLENGE_RESPONSE + "=" + URLEncoder.encode(Base64.encode(encrypted_response), "UTF-8");
//				System.out.println("url str: " + urlStr);
				URL url = new URL(urlStr);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();

				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				String line = null;
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				while( (line = in.readLine()) != null ) {
					bytes.write(line.getBytes());
				}
//				processAsXML(bytes);
				System.out.println("read: " + bytes.size() + " in " + (System.currentTimeMillis()-start) + " ms e2e");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	class DeregistrationRequest implements Runnable {
		private String key;
		
		public DeregistrationRequest( String key ) {
			this.key = key;
		}
		
		public void run() {
			try {
				
				String urlStr = "http://" + host + ":" + port + "/admin?delkey";
				HttpURLConnection conn = (HttpURLConnection) (new URL(urlStr)).openConnection();
				
				String userpass = "admin:";
				conn.setRequestProperty("Authorization", "Basic " + (new sun.misc.BASE64Encoder()).encode(userpass.getBytes("UTF-8")));
				
				conn.setRequestMethod("POST");
				conn.setDoOutput(true);
				
				Map<String, String> requestHeaders = new HashMap<String, String>();
				Map<String, String> formParams = new HashMap<String, String>();

				formParams.put("pubkey", key);
				
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
				
				String line = null;
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				while( (line = in.readLine()) != null ) {
					System.out.println(line);
				}
				
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	class RegistrationRequest implements Runnable {
		private String nick;
		private String base64Key;
		private KeyPair keys;

		public RegistrationRequest( KeyPair keys ) {
			this(keys, null);
		}
		
		public RegistrationRequest( KeyPair keys, String inNick ) {
			this.keys = keys;
			this.base64Key = CryptoUtils.getBase64FromKey(keys.getPublic());
			if( inNick != null ) {
				this.nick = inNick;
			} else { 
				nick = "r-" + base64Key.hashCode();
			}
		}
		
		public void run() {
			try {
				
				URL url = new URL(community_url);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setConnectTimeout(10*1000); // 10 second timeouts
				conn.setReadTimeout(10*1000);
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
				
				// TODO: add gzip processing here?
				
				Map<String, String> requestHeaders = new HashMap<String, String>();
				Map<String, String> formParams = new HashMap<String, String>();

				formParams.put("base64key", base64Key);
				formParams.put("nick", nick);
				
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
//					System.out.println("resp line: " + line);
				}

				in.close();

				System.out.println("final status code: " + conn.getResponseCode() + " / " + conn.getResponseMessage());
				registered.add(keys);
				
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	};
	
	
	public static final void main( String [] args ) throws Exception {

		TestEmbeddedServer test = null;
		if( args.length == 0 ) {
			test = new TestEmbeddedServer("https://ultramagnetic.dyn.cs.washington.edu", 8081);
		} else {
			(new TestEmbeddedServer(args[0], Integer.parseInt(args[1]))).doit();			
		}
		
		test.register_all();
		
	}
	
}
