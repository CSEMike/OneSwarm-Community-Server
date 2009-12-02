package edu.washington.cs.oneswarm.community2.test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyPair;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.community2.shared.CommunityConstants;
import edu.washington.cs.oneswarm.community2.utils.ByteManip;

public class TestRequest {

	public static final String BASE_URL = "http://127.0.0.1:8080/community";
	private KeyPair pair;
	private String base64PubKey;

	public TestRequest(KeyPair pair) {
		this.pair = pair;

		base64PubKey = Base64.encode(pair.getPublic().getEncoded());
	}

	public void run() {

		/**
		 * Three step: 1) get challenge, 2) send reponse, 3) parse list
		 */

		try {
			URL url = new URL(BASE_URL + "?" + CommunityConstants.BASE64_PUBLIC_KEY + "=" + URLEncoder.encode(base64PubKey, "UTF-8"));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String l = in.readLine();
			if (l != null) {
				System.out.println("got: " + l);
				
				if (l.startsWith(CommunityConstants.CHALLENGE)) {
					String[] toks = l.split("\\s+");
					if (toks.length != 2) {
						System.err.println("bad challenge");
						return;
					}

					long challenge = Long.parseLong(toks[1]);
					System.out.println("got challenge: " + challenge);

					reissueWithResponse(challenge);
				}
			} 
			

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void reissueWithResponse(long challenge) {
		try {

			byte[] encrypted_response = null;

			Signature sig = Signature.getInstance("SHA1withRSA");
			sig.initSign(pair.getPrivate());
			sig.update(ByteManip.ltob(challenge + 1));
			encrypted_response = sig.sign();

			String urlStr = BASE_URL + "?" + CommunityConstants.BASE64_PUBLIC_KEY + "=" + URLEncoder.encode(base64PubKey, "UTF-8") + "&" + CommunityConstants.CHALLENGE_RESPONSE + "=" + URLEncoder.encode(Base64.encode(encrypted_response), "UTF-8");
			System.out.println("url str: " + urlStr);
			URL url = new URL(urlStr);

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			readLimitedInto(conn, 2 * 1024 * 1024, bytes);

			System.out.println("read: " + bytes.size());

			processAsXML(bytes);

			// BufferedReader in = new BufferedReader(new
			// InputStreamReader(conn.getInputStream()));
			// String l = in.readLine();
			// while( l != null ) {
			// System.out.println("got: " + l);
			// l = in.readLine();
			// }
			System.out.println("done");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void processAsXML(ByteArrayOutputStream bytes) {

		ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray());

		try {
			TransformerFactory factory = TransformerFactory.newInstance();

			Transformer xformer = factory.newTransformer();

			Source source = new StreamSource(input);

			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = builder.newDocument();
			Result result = new DOMResult(doc);

			xformer.transform(source, result);

			NodeList root = doc.getElementsByTagName(CommunityConstants.RESPONSE_ROOT);
			Node response = root.item(0);
			String refreshInterval;
			NodeList firstLevel = response.getChildNodes();
			for( int i=0; i<firstLevel.getLength(); i++ ) {
				Node kid = firstLevel.item(i);
				if( kid.getLocalName().equals(CommunityConstants.REFRESH_INTERVAL) ) {
					refreshInterval = kid.getTextContent();
					System.out.println("got refresh interval: " + refreshInterval);
				} else if( kid.getLocalName().equals(CommunityConstants.FRIEND_LIST) ) {
					parseFriendList(kid);
				}
			}

		} catch (ParserConfigurationException e) {
			// couldn't even create an empty doc
		} catch (TransformerException e) {
			;
		} catch( NullPointerException e ) {
			// basically means the file had bad structure
			e.printStackTrace();
		}

	}

	private List<String[]> parseFriendList(Node kid) {
		List<String[]> out = new ArrayList<String[]>();
		for( int i=0; i<kid.getChildNodes().getLength(); i++ ) {
			Node entry = kid.getChildNodes().item(i);
			String key = entry.getAttributes().getNamedItem(CommunityConstants.KEY_ATTRIB).getTextContent();
			String nick = entry.getAttributes().getNamedItem(CommunityConstants.NICK_ATTRIB).getTextContent();
			
			System.out.println("parsed " + key + " / " + nick);
			
			out.add(new String[]{key, nick});
		}
		return out;
	}

	private InputStream getConnectionInputStream(HttpURLConnection conn) throws IOException {
		if (conn.getHeaderField("Content-Encoding") != null) {
			if (conn.getHeaderField("Content-Encoding").contains("gzip")) {
				return new GZIPInputStream(conn.getInputStream());
			}
		}
		return conn.getInputStream();
	}

	private void readLimitedInto(HttpURLConnection conn, int limit, ByteArrayOutputStream read) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(getConnectionInputStream(conn)));
		String line = null;
		while ((line = in.readLine()) != null) {
			read.write(line.getBytes());
			if (read.size() > limit) {
				return;
			}
		}
	}

	public static void main(String[] args) throws Exception {

		KeyPair pair = null;
		try {
			pair = (KeyPair) (new ObjectInputStream(new FileInputStream("/tmp/keys.scratch"))).readObject();
			System.out.println("loaded saved key pair");
		} catch (Exception e) {
			CryptoUtils c = new CryptoUtils();
			pair = c.getPair();
			System.out.println(Base64.encode(pair.getPublic().getEncoded()));

			ObjectOutputStream saved = new ObjectOutputStream(new FileOutputStream("/tmp/keys.scratch"));
			saved.writeObject(pair);
			System.out.println("generated/saved key pair");
		}

		System.out.println("pub");
		System.out.println(Base64.encode(pair.getPublic().getEncoded()));

		(new TestRequest(pair)).run();
	}

}
