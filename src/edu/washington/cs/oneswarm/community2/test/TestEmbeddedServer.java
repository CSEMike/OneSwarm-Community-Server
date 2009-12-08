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
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.community2.shared.CommunityConstants;
import edu.washington.cs.oneswarm.community2.utils.ByteManip;

class StopWatch {
	
	long start;
	
	public StopWatch() {
		start();
	}
	
	public void start() { 
		start = System.currentTimeMillis();
	}
	
	public long lap( String task ) { 
		long v = (System.currentTimeMillis()-start);
//		System.out.println(task + ":: " + v);
		start();
		return v;
	}
}

class LiberalTrustManager implements X509TrustManager
{
	public LiberalTrustManager() {}
	
	public void checkClientTrusted(X509Certificate[] certs, String authType) {}
	
	public void checkServerTrusted(X509Certificate[] certs, String authType) {}
	
	public X509Certificate[] getAcceptedIssuers() {
		return new java.security.cert.X509Certificate[0];
	}
}

public class TestEmbeddedServer {
	
	private int port;
	private String host;
	private String community_url;

	List<KeyPair> generatedKeys = new LinkedList<KeyPair>();
	private ExecutorService threadPool;
	private SSLContext sslcontext;
	
	public static final String SCRATCH_PATH = "scratch_keys";
	
	public TestEmbeddedServer( String host, int port ) {
		this.host = host;
		this.port = port;
		
		this.community_url = host + ":" + port + "/community";
		
		getKeys();
		
		TrustManager[] osTrustManager = new TrustManager[] {
				new LiberalTrustManager()
			};

		try {
			sslcontext = SSLContext.getInstance("SSL");
			sslcontext.init(null, osTrustManager, null);
		} catch (NoSuchAlgorithmException e) {
			System.err.println(e);
			e.printStackTrace();
		} catch (KeyManagementException e) {
			System.err.println(e);
			e.printStackTrace();
		}
		
		threadPool = Executors.newFixedThreadPool(300, new ThreadFactory(){
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "Request thread pool thread");
//				t.setDaemon(true);
				return t;
			}});
	}
	
	private void getKeys() {
		try {
			generatedKeys = (List<KeyPair>)((new ObjectInputStream(new FileInputStream(SCRATCH_PATH))).readObject());
		} catch( Exception e ) {
			System.err.println("couldn't get scratch keys: " + e.toString());
			System.out.println("generating keys...");
			
			CryptoUtils c = new CryptoUtils();
			generatedKeys = new LinkedList<KeyPair>();
			for( int i=0; i<200; i++ ) {
				if( (i%100) == 0 ) {
					System.out.println("done " + i);
				}
				
				generatedKeys.add(c.getPair());
			}
			System.out.println("done, writing...");
			try {
				(new ObjectOutputStream(new FileOutputStream(SCRATCH_PATH))).writeObject(generatedKeys);
			} catch (Exception e2 ) {
				e.printStackTrace();
			}
			System.out.println("done");
		}
	}
	
	CDF reg_connections = new CDF("reg_connections");
	CDF reg_io = new CDF("reg_io");
	
	CDF ref_connections = new CDF("ref_connections");
	CDF ref_io = new CDF("ref_io");
	
	public void bench_key_registrations() {
		long start = System.currentTimeMillis();
		register_all();
		System.out.println("register all took: " + (System.currentTimeMillis() - start));
		reg_connections.draw();
		reg_io.draw();
	}
	
	public void bench_refreshes() {
		
		refreshed.clear();
		refreshing_error.clear();
		
		long start = System.currentTimeMillis();
		for( KeyPair p : generatedKeys ) {
			threadPool.submit(new PeerRequest(p));
		}
		while( refreshed.size() < generatedKeys.size() ) {
			System.out.println("done refreshing: " + refreshed.size());
			try {
				Thread.sleep(1000);
			} catch( Exception e ) {}
		}
		System.out.println("alldone -- errors: " + refreshing_error.size());
		
		ref_io.draw();
		ref_connections.draw();
		
	}
	
//	private void try_unicode() { 
//		
//		KeyPair pair = generatedKeys.remove(0);
//		threadPool.submit(new RegistrationRequest(pair, "ŽfadfdafŒaŸber"));
//		
//		try {
//			Thread.sleep(5000);
//		} catch( Exception e ) {}
//		
//	}
	
	private void register_all() {
		
		registered.clear();
		errors.clear();
		
		for( KeyPair p : generatedKeys ) {
			threadPool.submit(new RegistrationRequest(p));
		}
		
		while( registered.size() + errors.size() < generatedKeys.size() ) {
			try {
				Thread.sleep(5*1000);
			} catch( Exception e ) {}
			System.out.println("registered: " + registered.size() + " error: " + errors.size());
		}
	}
	
	final List<KeyPair> registered = Collections.synchronizedList(new ArrayList<KeyPair>());
	final List<KeyPair> errors = Collections.synchronizedList(new ArrayList<KeyPair>());
	
	final Set<String> recently_challenged = Collections.synchronizedSet(new HashSet<String>());
	final List<KeyPair> refreshed = Collections.synchronizedList(new ArrayList<KeyPair>());
	final List<KeyPair> refreshing_error = Collections.synchronizedList(new ArrayList<KeyPair>());
	
	
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
			
			HttpURLConnection conn = null;
			
			try {
				
				start = System.currentTimeMillis();
				
				String theURLString = community_url + "?" + CommunityConstants.BASE64_PUBLIC_KEY + "=" + URLEncoder.encode(base64Key, "UTF-8");
				
				URL url = new URL(theURLString);
				conn = (HttpURLConnection) url.openConnection();
				
				if( conn instanceof HttpsURLConnection ) {
					try {
						((HttpsURLConnection) conn).setSSLSocketFactory(sslcontext.getSocketFactory());
					} catch( Exception e ) {
						e.printStackTrace();
						throw new IOException(e.getMessage());
					}
				}
				
				conn.setConnectTimeout(10*1000); // 10 second timeouts
				conn.setReadTimeout(60*1000);
				conn.setRequestMethod("GET");
				
				StopWatch watch = new StopWatch();
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				
				ref_connections.addValue(watch.lap("Connect1"));
				
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
					reissueWithResponse(challenge, watch);
				} else {
					System.err.println("Didn't get challenge, got: " + l);
				}
				
			} catch( Exception e ) {
				refreshing_error.add(keys);
				e.printStackTrace();
			} finally { 
				recently_challenged.remove(CryptoUtils.getBase64FromKey(keys.getPublic()));
				refreshed.add(keys);
				
				if( conn != null ) {
					try {
						conn.getOutputStream().close();
						conn.getInputStream().close();
					} catch( IOException e ) {}
					conn.disconnect();
				}
			}
		}
		
		public void reissueWithResponse( long challenge, StopWatch watch ) {
			HttpURLConnection conn = null;
			try {
				byte[] encrypted_response = null;

				Signature signer = Signature.getInstance("SHA1withRSA");
				signer.initSign(keys.getPrivate());
				signer.update(ByteManip.ltob(challenge+1));
				encrypted_response = signer.sign();

				String urlStr = community_url + "?" + CommunityConstants.BASE64_PUBLIC_KEY + "=" + URLEncoder.encode(base64Key, "UTF-8") + "&" + CommunityConstants.CHALLENGE_RESPONSE + "=" + URLEncoder.encode(Base64.encode(encrypted_response), "UTF-8");
//				System.out.println("url str: " + urlStr);
				URL url = new URL(urlStr);
				
				watch.lap("intermediary tasks");
				
				conn = (HttpURLConnection) url.openConnection();
				
				if( conn instanceof HttpsURLConnection ) {
					try {
						((HttpsURLConnection) conn).setSSLSocketFactory(sslcontext.getSocketFactory());
					} catch( Exception e ) {
						e.printStackTrace();
						throw new IOException(e.getMessage());
					}
				}

				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				String line = null;
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				
				ref_connections.addValue(watch.lap("connect2"));
				
				while( (line = in.readLine()) != null ) {
					bytes.write(line.getBytes());
				}
//				processAsXML(bytes);
				//System.out.println("read: " + bytes.size() + " in " + (System.currentTimeMillis()-start) + " ms e2e");
				ref_io.addValue(watch.lap("read response to 2"));
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if( conn != null ) {
					conn.disconnect();
				}
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
				
				if( conn instanceof HttpsURLConnection ) {
					try {
						((HttpsURLConnection) conn).setSSLSocketFactory(sslcontext.getSocketFactory());
					} catch( Exception e ) {
						e.printStackTrace();
						throw new IOException(e.getMessage());
					}
				}
				
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
				
				StopWatch timer = new StopWatch();
				
				OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
				
				reg_connections.addValue(timer.lap("initial connection"));
				
				Iterator<String> params = formParams.keySet().iterator();
				while (params.hasNext()) {
					String name = params.next();
					String value = formParams.get(name);

					out.append(URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8"));
					if (params.hasNext()) {
						out.append("&");
					}
				}

				long start = System.currentTimeMillis();
				out.flush();

				timer.lap("wrote params");
			

				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String line = null;
				while ((line = in.readLine()) != null) {
//					System.out.println("resp line: " + line);
				}

				timer.lap("read response");
				
				in.close();
				
				reg_io.addValue(System.currentTimeMillis()-start);

				System.out.println("final status code: " + conn.getResponseCode() + " / " + conn.getResponseMessage());
				registered.add(keys);
				
			} catch( Exception e ) {
				e.printStackTrace();
				errors.add(keys);
			}
		}
	};
	
	private void single_request() {
		
		KeyPair p = generatedKeys.get(0);
		
//		threadPool.submit(new RegistrationRequest(p));
		threadPool.submit(new PeerRequest(p));
		
	}
	
	
	
	public static final void main( String [] args ) throws Exception {

		TestEmbeddedServer test = null;
		if( args.length == 0 ) {
			test = new TestEmbeddedServer("https://ultramagnetic.dyn.cs.washington.edu", 8081);
//			test = new TestEmbeddedServer("http://127.0.0.1", 8081);
		} else {
			test = (new TestEmbeddedServer(args[0], Integer.parseInt(args[1])));			
		}
		
//		test.bench_key_registrations();
//		while( true ) {
			test.bench_refreshes();
//		}
//		test.single_request();
		
	}

}
