package edu.washington.cs.oneswarm.community2.server;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.community2.shared.CommunityConstants;
import edu.washington.cs.oneswarm.community2.shared.KeyRegistrationRecord;
import edu.washington.cs.oneswarm.community2.utils.ByteManip;

public class KeyRegistrationServlet extends javax.servlet.http.HttpServlet {
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(KeyRegistrationServlet.class.getName());

	/**
	 * We use this to rate-limit posts to limit DoS. A single IP can only
	 * register a new user once every MIN_REGISTRATION_INTERVAL_MS (assuming we
	 * don't overflow the recentPosts table during that time)
	 */
	private static final long MIN_REGISTRATION_INTERVAL_MS = 10 * 1000;
	static Map<String, Long> recentPosts = Collections.synchronizedMap(new LinkedHashMap<String, Long>() {
		protected boolean removeEldestEntry(Map.Entry<String, Long> ent) {
			return size() > 1000;
		}
	});

	/**
	 * This stores the challenges issued to recent connections and is soft
	 * state.
	 */
	static Map<String, Long> recentChallenges = Collections.synchronizedMap(new LinkedHashMap<String, Long>() {
		protected boolean removeEldestEntry(Map.Entry<String, Long> ent) {
			return size() > 1000;
		}
	});

	public KeyRegistrationServlet() {
		CommunityDAO.get();
		logger.info("Key registration servlet created.");
	}
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		logger.finest("get request: " + request.toString());
		
		/**
		 * Sanity checking -- does this request have a key?
		 */
		if (request.getParameter(CommunityConstants.BASE64_PUBLIC_KEY) == null) {
			logger.warning("GET request with no key from: " + request.getRemoteAddr());
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}

		/**
		 * First the easy cases -- do we even _know_ about this key?
		 */
		if (CommunityDAO.get().isRegistered(request.getParameter(CommunityConstants.BASE64_PUBLIC_KEY)) == false) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			logger.finer("Get request and we don't know about the key " + request.getRemoteAddr());
			return;
		}

		if (request.getParameter(CommunityConstants.CHALLENGE_RESPONSE) != null) {
			logger.finer("Get request with challenge response, checking...");
			processChallengeResponse(request, response);
		} else {
			logger.finer("Get request without challenge, sending challenge...");
			sendChallenge(request, response);
		}
	}

	private void sendChallenge(HttpServletRequest request, HttpServletResponse response) {
		try {
			PrintStream out = new PrintStream(new BufferedOutputStream(response.getOutputStream()));

			long challenge = (long) ((Long.MAX_VALUE - 1) * Math.random());
			recentChallenges.put(request.getParameter(CommunityConstants.BASE64_PUBLIC_KEY), challenge);
			out.println(CommunityConstants.CHALLENGE + " " + challenge);
			logger.finer("Issued challenge -- " + challenge + " -- to " + request.getRemoteAddr());
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			logger.warning(e.toString());
		}
	}

	private void processChallengeResponse(HttpServletRequest request, HttpServletResponse response) {
		try {
			String base64_key = request.getParameter(CommunityConstants.BASE64_PUBLIC_KEY);
			String base64_response = request.getParameter(CommunityConstants.CHALLENGE_RESPONSE);
				
			/**
			 * First the easy cases -- do we even _know_ about this key? If not,
			 * no need to do crypto.
			 * 
			 * This may also happen if we 1) just pruned the database, 2) and this user was in the middle of a refresh
			 * (although this is pretty unlikely)
			 */
			if (CommunityDAO.get().isRegistered(base64_key) == false) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				logger.finer("Challenge request with unknown key, dropping (unauthorized) " + request.getRemoteAddr());
				return;
			}

			if (recentChallenges.containsKey(base64_key) == false) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				logger.finer("Challenge request without recent issue, dropping (unauthorized) " + request.getRemoteAddr());
				return;
			}

			long originalChallenge = recentChallenges.get(base64_key);

			byte[] key_bytes = Base64.decode(base64_key);
			if (key_bytes == null) {
				logger.warning("Couldn't decode key bytes from " + request.getRemoteAddr() + " / " + base64_key);
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			byte[] response_bytes = Base64.decode(base64_response);
			if (response_bytes == null) {
				logger.warning("Couldn't decode challenge response from " + request.getRemoteAddr() + " / " + base64_response);
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(key_bytes);
			KeyFactory factory = KeyFactory.getInstance("RSA");

			PublicKey pub = null;
			try {
				pub = factory.generatePublic(pubKeySpec);
			} catch (InvalidKeySpecException e) {
				logger.warning("Couldn't decode valid public key from " + request.getRemoteAddr() + " / " + base64_response);
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			Signature sig = Signature.getInstance("SHA1withRSA");
			sig.initVerify(pub);
			sig.update(ByteManip.ltob(originalChallenge + 1));

			if (sig.verify(response_bytes)) {
				logger.fine("Signature verified, generating response " + request.getRemoteAddr());
				generateAndSendKeyList(request, response);
			} else {
				logger.warning("Key failed challenge/response. Expected: " + (originalChallenge + 1) + " received signature didn't verify from " + request.getRemoteAddr());
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			logger.severe(e.toString());
		} catch (InvalidKeyException e) {
			System.err.println(e);
			e.printStackTrace();
		} catch (SignatureException e) {
			System.err.println(e);
			e.printStackTrace();
		}
	}

	private void generateAndSendKeyList(HttpServletRequest request, HttpServletResponse response) {
		List<KeyRegistrationRecord> nearest = CommunityDAO.get().getPeers(request.getParameter(CommunityConstants.BASE64_PUBLIC_KEY));
		
		if (nearest == null) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			logger.warning("Got null set of out keys");
			return;
		}
		
		logger.fine("Got " + nearest.size() + " nearest peers for request " + request.getRemoteAddr());

		/**
		 * If this is a request for an authenticated server, add the account names 
		 * to the returned nicknames. But, don't do this to the FriendRecord since 
		 * that is persistent. 
		 */
		Map<Long, CommunityAccount> id_to_rec = new HashMap<Long, CommunityAccount>();
		if( request.getUserPrincipal() != null ) {
			for( CommunityAccount rec : CommunityDAO.get().getAccounts() ) {
				id_to_rec.put(rec.getID(), rec);
			}
		}
		
		String encoding = request.getHeader("Accept-Encoding");
		boolean supportsGzip = false;
		if (encoding != null) {
			if (encoding.toLowerCase().indexOf("gzip") > -1) {
				supportsGzip = true;
				logger.finer("Client accepts gzip: " + request.getRemoteAddr());
			}
		}

		OutputStream responseOut = null;
		try {
			if (supportsGzip == true) {
				response.setHeader("Content-Encoding", "gzip");
				responseOut = new GZIPOutputStream(response.getOutputStream());
			} else {
				responseOut = response.getOutputStream();	
			}
		} catch (IOException e) {
			e.printStackTrace();
			logger.warning(e.toString());
		} 

		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.newDocument();
			Element root = doc.createElement(CommunityConstants.RESPONSE_ROOT);

			doc.appendChild(root);

			Element refreshTime = doc.createElement(CommunityConstants.REFRESH_INTERVAL);
			refreshTime.setTextContent(Integer.toString(Integer.parseInt(System.getProperty(EmbeddedServer.Setting.REFRESH_INTERVAL.getKey())) * 60));
			root.appendChild(refreshTime);

			Element friendList = doc.createElement(CommunityConstants.FRIEND_LIST);
			root.appendChild(friendList);
			for (KeyRegistrationRecord rec : nearest) {
				Element friend = doc.createElement(CommunityConstants.FRIEND);
				friend.setAttribute(CommunityConstants.KEY_ATTRIB, rec.getBase64PublicKey());
				
				/**
				 * Possible that it might not if a user was deleted after we obtain the nearest peers 
				 * but before we obtain the account list 
				 */
				String acct = "";
				if( id_to_rec.containsKey(rec.getCreatedByID()) && 
					System.getProperty(EmbeddedServer.Setting.INCLUDE_USERNAME_WITH_NICKNAME.getKey()).equals(Boolean.TRUE.toString()) ) {
					acct += " (" + id_to_rec.get(rec.getCreatedByID()).getName() + ")";
				}
				
				friend.setAttribute(CommunityConstants.NICK_ATTRIB, rec.getNickname() + acct);
				friendList.appendChild(friend);
			}

			TransformerFactory transFactory = TransformerFactory.newInstance();
			Transformer trans = transFactory.newTransformer();
			trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			trans.setOutputProperty(OutputKeys.INDENT, "yes");

			StringWriter sw = new StringWriter();
			StreamResult result = new StreamResult(sw);
			DOMSource src = new DOMSource(doc);
			trans.transform(src, result);
			responseOut.write(sw.toString().getBytes("UTF8"));
			responseOut.flush();

			System.out.println(sw.toString());

			logger.finest("XML write done. finished " + request.getRemoteAddr());

		} catch (IOException e) {
			logger.warning(e.toString());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		} catch (ParserConfigurationException e) {
			logger.warning(e.toString());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		} catch (TransformerException e) {
			logger.warning(e.toString());
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		} finally {
			if( responseOut != null ) {
				try { responseOut.close(); } catch( IOException e ) {}
			}
		}
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		
		if( logger.isLoggable(Level.FINEST) ) { 
			logger.finest("Got post: " + request.toString());
		}
		
		try {
			request.setCharacterEncoding("UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			logger.warning(e1.toString());
		}
		
		/**
		 * Check for flooding, except from localhost (used for stress-testing)
		 */
		if( request.getRemoteAddr().equals("127.0.0.1") == false ) {
			if (recentPosts.containsKey(request.getRemoteAddr())) {
				if (recentPosts.get(request.getRemoteAddr()) + MIN_REGISTRATION_INTERVAL_MS > System.currentTimeMillis()) {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					logger.warning("Flooding from: " + request.getRemoteAddr());
					try {
						response.getOutputStream().write(CommunityConstants.REGISTRATION_RATE_LIMITED.getBytes());
						response.getOutputStream().flush();
						response.getOutputStream().close();
					} catch (IOException e) {
						logger.warning(e.toString());
					}
					recentPosts.put(request.getRemoteAddr(), System.currentTimeMillis());
					return;
				}
			}
			recentPosts.put(request.getRemoteAddr(), System.currentTimeMillis());
		}

		/**
		 * Actual request processing
		 */
		registerUser(request, response);
	}

	private void registerUser(HttpServletRequest request, HttpServletResponse response) {
		String key = request.getParameter(CommunityConstants.BASE64_PUBLIC_KEY);
		String nick = request.getParameter(CommunityConstants.NICKNAME);
		String remote_ip = request.getRemoteAddr();
		
		String username = request.getRemoteUser();
		logger.info("Registration request, username: "+  username + " / nick: " + nick);

		if (key == null) {
			logger.warning("Dropping registration request with null key from " + remote_ip);
			return;
		}

		if (nick == null) {
			logger.warning("Dropping registration request with null nick from " + remote_ip);
			return;
		}
		
		if( username == null) {
			logger.finer("Open server, using admin to register");
			username = "admin";
		}
		
		if (nick.length() > CommunityConstants.MAX_NICK_LENGTH) {
			logger.warning("Truncating lengthy nick: " + nick);
			nick = nick.substring(0, CommunityConstants.MAX_NICK_LENGTH);
		}

		logger.finer("Registration request: key=" + key + " remote_ip=" + remote_ip);

		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
			try {
				CommunityDAO.get().registerUser(key, nick, remote_ip, username);

				response.setStatus(HttpServletResponse.SC_OK);
				out.append(CommunityConstants.REGISTRATION_SUCCESS);

				logger.finer("Successfully registered " + nick + " from " + request.getRemoteAddr());
			} catch (DuplicateRegistrationException e) {

				logger.finer("Duplicate registration " + nick + " from " + request.getRemoteAddr());
				response.setStatus(HttpServletResponse.SC_CONFLICT);
				out.append(CommunityConstants.REGISTRATION_DUPLICATE);
			} catch( TooManyRegistrationsException e ) {
				
				logger.finer(e.toString() + " / " + remote_ip);
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				out.append(e.toString());
				
			} catch (Exception e) {
				e.printStackTrace();
				logger.warning(e.toString());

				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			} finally {
				out.flush();
				out.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
