package edu.washington.cs.oneswarm.community2.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.NCSARequestLog;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.RequestLogHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.SecurityHandler;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.webapp.JettyWebXmlConfiguration;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.webapp.WebInfConfiguration;
import org.mortbay.jetty.webapp.WebXmlConfiguration;
import org.mortbay.thread.QueuedThreadPool;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.community2.shared.KeyRegistrationRecord;
import edu.washington.cs.oneswarm.community2.utils.GangliaStat;
import edu.washington.cs.oneswarm.community2.utils.IPFilter;
import edu.washington.cs.oneswarm.community2.utils.IPServletFilter;
import edu.washington.cs.oneswarm.community2.utils.GangliaStat.StatReporter;

public class EmbeddedServer {

	public enum StartupSetting {
		MAX_THREADS("max.threads", Integer.valueOf(30)),
		
		GANGLIA_HOST("ganglia.host", null), 
		GANGLIA_PORT("ganglia.port", Integer.valueOf(8649)),
		
		IP_WHITELIST("ip.whitelist", null), 
		IP_BLACKLIST("ip.blacklist", null),
		
		HOST("host", null), 
		PORT("port", Integer.valueOf(8080)),
		
		SSL("ssl", null),
		KEYSTORE_PASSWORD("keystore.password", null), 
		UNENCRYPTED_PORT("unencrypted.port", null), 
		
		INFRASTRUCTURE_PEERS("infrastructure.peers", null),
		
		REQUIRE_AUTH_FOR_KEY_REGISTRATION("require.auth.for.key.registration", Boolean.FALSE), 
		REQUIRE_AUTH_FOR_PUBLISH("require.auth.for.publish", Boolean.TRUE),
		REQUEST_LOG_DIRECTORY("request.log.directory", null), 
		REQUEST_LOG_RETAIN_DAYS("request.log.retain.days", Integer.valueOf(7)),
		
		JDBC_PROPS("jdbc.properties", "/tmp/jdbc.properties");

		;

		private String key;
		private Object defaultValue;

		public String getKey() {
			return key;
		}

		public Object getDefaultValue() {
			return defaultValue;
		}

		private StartupSetting(String key, Object defaultValue) {
			this.key = key;
			this.defaultValue = defaultValue;
		}
	}

	public enum Setting {

		REFRESH_INTERVAL("oneswarm.community.refresh.interval", "The interval between client refreshes of friend lists.", new Integer(10)), 
		MAX_FRIENDS_RETURNED("max.friends.to.return", "The number of friends to return.", new Integer(26)), 
		KEY_EXPIRATION_SECONDS("user.expiration.seconds", "The interval after which inactive keys expire.", new Integer(86400)),
		
		REQUIRE_SWARM_MODERATION("require.swarm.moderation", "Don't show submitted swarms until reviewed by a moderator.", Boolean.FALSE), 
		STORE_TORRENTS("store.torrents", "Store magnet links only. Discard piece data, etc.", Boolean.FALSE), 
		DISCARD_PREVIEWS("discard.previews", "Discard submitted previews.", Boolean.FALSE), 
		DONT_DISPLAY_PREVIEWS("dont.display.previews", "Store submitted previews, but do not show them to users.", Boolean.FALSE),
		RETAIN_ACCOUNT_INFO("retain.account.info", "Log account names when swarms are submitted.", Boolean.TRUE),
		
		DISABLE_COMMENTS("disable.user.comments", "Disallow all comments, even for registered users.", Boolean.FALSE),
		KEEP_COMMENT_IPS("keep.comment.ips", "Retain the IP address of users making comments.", Boolean.TRUE),
		DISPLAY_COMMENT_IPS_MODERATORS("display.comment.ips.moderators", "Display comment IPs, if saved, to moderators.", Boolean.TRUE), 
		
		KEY_REG_LIMIT_IP("key.registration.limit.ip.default", "The default number of keys that can be registered by a single IP.", new Integer(5)), 
		KEY_REG_LIMIT_ACCOUNT("key.registration.limit.account.default", "The default key registration limit per account.", new Integer(5)),
		
		SWARMS_PER_PAGE("swarms.per.page", "Number of swarms per-page in category results and files.jsp page.", new Integer(30)),
		SWARMS_PER_SEARCH("swarms.per.search.result.page", "Number of swarms displayed per-page in search results.", new Integer(30)), 
		
		ALLOW_SIGNUPS("allow.signup", "Allow account creation (other than by administrator).", Boolean.TRUE), 
		REQUIRE_CAPTCHA("signup.requires.captcha", "Require users to complete a CAPTCHA during signup.", Boolean.TRUE),
		
		SERVER_NAME("community.server.name", "The server name shown to users.", "OneSwarm Community Server"),
		MOTD("motd", "Message of the day.", null), 
		ENABLE_RSS("enable.rss.feeds", "Provide RSS feeds.", Boolean.FALSE), 
		RSS_BASE_URL("rss.base.url", "The base URL to use when generating RSS feeds.", ""),
		
		INCLUDE_USERNAME_WITH_NICKNAME("include.username.with.nickname", "When returning friends, include both nickname and username.", Boolean.FALSE),
		ALLOW_USER_PUBLISHING("allow.user.publishing", "Allow users to publish. (If false, only moderators can publish.)", Boolean.TRUE), 
		
		;

		private String key, help;
		private Object defaultValue;

		public String getKey() {
			return key;
		}

		public String getHelp() {
			return help;
		}

		public Object getDefaultValue() {
			return defaultValue;
		}

		private Setting(String key, String help, Object defaultValue) {
			this.key = key;
			this.help = help;
			this.defaultValue = defaultValue;
		}
	};

	private static Logger logger = Logger.getLogger(EmbeddedServer.class.getName());

	Server mServer = null;

	static final class OurHashRealm extends HashUserRealm {

		public OurHashRealm() {
			super("OneSwarm Community Server");
		}

		public Principal authenticate(String username, Object credentials, Request request) {

			if (credentials instanceof String) {
				Principal p = CommunityDAO.get().authenticate(username, (String) credentials);
				return p;
			}
			return null;
		}

		public boolean isUserInRole(Principal p, String role) {
			if (p instanceof CommunityAccount) {
				logger.finer("isUserInRole " + p + " / " + role);
				
				CommunityAccount cast = (CommunityAccount)p;
				for( String userRole : cast.getRoles() ) { 
					if( userRole.equals(role) ) { 
						return true;
					}
				}
				
			}
			return false;
		}

	}

	public EmbeddedServer(String inHost, int inPort, int inMaxThreads, String keystorePath, final List<IPFilter> whitelist, final List<IPFilter> blacklist) {

		CommunityDAO.get();

		mServer = new Server();

		QueuedThreadPool threadPool = new QueuedThreadPool();
		threadPool.setMinThreads(2);
		threadPool.setMaxThreads(inMaxThreads);
		threadPool.setName("Jetty embedded server thread pool");
		threadPool.setDaemon(true);

		logger.info("max_threads: " + inMaxThreads);

		mServer.setThreadPool(threadPool);

		Connector connector;

		/**
		 * We'll at least use this SecurityHandler for the administrator
		 * interface -- we may also use it for user authentication (if this is
		 * an authorized-users-only server)
		 */

		// Constraint constraint = new Constraint();
		// constraint.setName(Constraint.__BASIC_AUTH);
		// constraint.setRoles(new String[] { ADMIN_ROLE });
		// constraint.setAuthenticate(true);

		// cm.setConstraint(constraint);
		// cm.setPathSpec(ADMIN_SERVLET_PATH + "/*");
		// constraintMappings.add(cm);
		//		
		// cm = new ConstraintMapping();
		// cm.setConstraint(constraint);
		// cm.setPathSpec("/CommunityServerAdmin.html");
		// constraintMappings.add(cm);

		boolean requireAuthentication = true;

		/**
		 * Install the key registration servlet
		 */
		Context registrationContext = new Context(mServer, "/community");
		SecurityHandler secHandler = new SecurityHandler();
		secHandler.setUserRealm(new OurHashRealm());
		
		/**
		 * We need to support POST messages at /community to retain backwards compatibility 
		 * with 0.6.5 clients. 
		 */
		registrationContext.setAllowNullPathInfo(true);
		
		List<ConstraintMapping> constraintMappings = new ArrayList<ConstraintMapping>();
		ConstraintMapping cm = new ConstraintMapping();

		registrationContext.setSecurityHandler(secHandler);
		registrationContext.addServlet(new ServletHolder(new KeyRegistrationServlet()), "/");
		
		registrationContext.addFilter(new FilterHolder(new IPServletFilter(whitelist, blacklist)), "/*", org.mortbay.jetty.Handler.ALL);

		/**
		 * Authentication constraint for requesting peers
		 */
		if (System.getProperty(StartupSetting.REQUIRE_AUTH_FOR_KEY_REGISTRATION.getKey()).equals(Boolean.TRUE.toString())) {

			logger.info("Authentication required for key registration / requests");

			Constraint constraint = new Constraint();
			constraint.setName(Constraint.__BASIC_AUTH);
			constraint.setRoles(new String[] { "user", "admin", "moderator" });
			constraint.setAuthenticate(true);

			cm = new ConstraintMapping();
			cm.setConstraint(constraint);
			cm.setPathSpec("/*");
			constraintMappings.add(cm);
		}
		secHandler.setConstraintMappings(constraintMappings.toArray(new ConstraintMapping[0]));

		/**
		 * Install the publishing servlet
		 */
		Context publishContext = new Context(mServer, "/publish");
		secHandler = new SecurityHandler();
		secHandler.setUserRealm(new OurHashRealm());
		publishContext.setSecurityHandler(secHandler);
		publishContext.addServlet(new ServletHolder(new SwarmPublishServlet()), "/");

		publishContext.addFilter(new FilterHolder(new IPServletFilter(whitelist, blacklist)), "/*", org.mortbay.jetty.Handler.ALL);

		/**
		 * Authentication constraint for publishing swarms
		 */
		if (System.getProperty(StartupSetting.REQUIRE_AUTH_FOR_PUBLISH.getKey()).equals(Boolean.TRUE.toString())) {

			logger.info("Authentication required for swarm publishing");

			Constraint constraint = new Constraint();
			constraint.setName(Constraint.__BASIC_AUTH);
			constraint.setRoles(new String[] { "user", "admin", "moderator" });
			constraint.setAuthenticate(true);

			cm = new ConstraintMapping();
			cm.setConstraint(constraint);
			cm.setPathSpec("/*");
			constraintMappings.add(cm);
		}

		secHandler.setConstraintMappings(constraintMappings.toArray(new ConstraintMapping[0]));

		/**
		 * Install the JSP interface.
		 */
		WebAppContext app = new WebAppContext();
		app.setContextPath("/");
		app.setWar("./war");
		app.setConfigurationClasses(new String[] { WebInfConfiguration.class.getName(), WebXmlConfiguration.class.getName(), JettyWebXmlConfiguration.class.getName() });
		app.setParentLoaderPriority(true);
		
		app.getInitParams().put("org.mortbay.jetty.servlet.Default.dirAllowed", "false");
		app.getInitParams().put("org.mortbay.jetty.servlet.Default.maxCacheSize", "0");
		app.getInitParams().put("org.mortbay.jetty.servlet.Default.cacheControl", "no-store,no-cache,must-revalidate");

		app.addFilter(new FilterHolder(new IPServletFilter(whitelist, blacklist)), "/*", org.mortbay.jetty.Handler.ALL);

		if (keystorePath != null) {

			logger.info("Using SSL...");

			/**
			 * see: http://docs.codehaus.org/display/JETTY/How+to+configure+SSL
			 */
			connector = new SslSocketConnector();
			final SslSocketConnector sslconnector = (SslSocketConnector) connector;
			sslconnector.setKeystore(keystorePath);
			sslconnector.setPassword(System.getProperty("jetty.ssl.password"));
			sslconnector.setKeyPassword(System.getProperty("jetty.ssl.keypassword"));
			sslconnector.setNeedClientAuth(false);
			sslconnector.setWantClientAuth(false);

		} else {
			connector = new SelectChannelConnector();
		}
		connector.setMaxIdleTime(5000);
		if (inHost != null) {
			connector.setHost(inHost);
			logger.info("host: " + inHost);
		}
		connector.setPort(inPort);
		mServer.addConnector(connector);
		
		Handler[] handlers = null;
		if (System.getProperty(StartupSetting.REQUEST_LOG_DIRECTORY.getKey()) != null) {

			RequestLogHandler requestLogHandler = new RequestLogHandler();
			NCSARequestLog requestLog = new NCSARequestLog(System.getProperty(StartupSetting.REQUEST_LOG_DIRECTORY.getKey()) + "/communityserver-yyyy_mm_dd.request.log");
			requestLog.setRetainDays(Integer.parseInt(System.getProperty(StartupSetting.REQUEST_LOG_RETAIN_DAYS.getKey())));
			requestLog.setAppend(true);
			requestLog.setExtended(true);
			requestLog.setLogTimeZone("GMT");
			requestLogHandler.setRequestLog(requestLog);

			handlers = new Handler[] { registrationContext, publishContext, app, requestLogHandler };
		} else {
			handlers = new Handler[] { registrationContext, publishContext, app };
		}

		mServer.setHandlers(handlers);
		
		logger.info("port: " + inPort);
	}

	public void start() {
		try {
			mServer.start();

			logger.info("started embedded server" );

		} catch (UnrecoverableKeyException e) {
			e.printStackTrace();
			logger.severe(e.toString());
			System.err.println("Did you remember to set the keystore password property?");
		} catch (Exception e) {
			e.printStackTrace();
			logger.severe(e.toString());
		}
	}

	private static final void set_default_settings() {
		for (Setting s : Setting.values()) {
			if (s.getDefaultValue() != null) {
				System.setProperty(s.getKey(), s.getDefaultValue() != null ? s.getDefaultValue().toString() : null);
			} else {
				System.clearProperty(s.getKey());
			}
		}

		for (StartupSetting s : StartupSetting.values()) {
			if (s.getDefaultValue() != null) {
				System.setProperty(s.getKey(), s.getDefaultValue().toString());
			} else {
				System.clearProperty(s.getKey());
			}
		}
	}

	private static final void load_config(String path) {
		Properties config = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(path);
			config.load(fis);
		} catch (Exception e ) {
			System.err.println("Error loading configuration file: " + path + "\n" + e.toString());
			System.exit(-1);
		} finally {
			try {
				if( fis != null ) {
					fis.close();
				}
			} catch( IOException e ) {}
		}
		Enumeration<String> params = (Enumeration<String>) config.propertyNames();
		while (params.hasMoreElements()) {
			String k = params.nextElement();
			System.setProperty(k, config.getProperty(k));
		}

		/**
		 * Now write out the revised JDBC properties file
		 */
		Properties jdbc = new Properties();

		jdbc.setProperty("jdbcdriver", "com.mysql.jdbc.Driver");
		jdbc.setProperty("url", "jdbc:mysql://" + System.getProperty("db.host") + ":" + System.getProperty("db.port") + "/" + System.getProperty("db.name"));
		jdbc.setProperty("username", System.getProperty("db.user"));
		jdbc.setProperty("password", System.getProperty("db.password"));
		jdbc.setProperty("usertable", "valid_accounts");
		jdbc.setProperty("usertablekey", "uid");
		jdbc.setProperty("usertableuserfield", "username");
		jdbc.setProperty("usertablepasswordfield", "password_hash");
		jdbc.setProperty("roletable", "roles");
		jdbc.setProperty("roletablekey", "role_id");
		jdbc.setProperty("roletablerolefield", "role");
		jdbc.setProperty("userroletable", "user_roles");
		jdbc.setProperty("userroletableuserkey", "uid");
		jdbc.setProperty("userroletablerolekey", "role_id");
		jdbc.setProperty("cachetime", "0");

		FileOutputStream outStream = null;
		try {
			outStream = new FileOutputStream(System.getProperty("jdbc.properties"));
			jdbc.store(outStream, null);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try { 
				outStream.close();
			} catch( Exception e ) {}
		}

	}

	public static final void main(String[] args) {

		set_default_settings();
		load_config(args.length == 0 ? "community.conf" : args[0]);

		String host = System.getProperty(StartupSetting.HOST.getKey());
		int port = Integer.parseInt(System.getProperty(StartupSetting.PORT.getKey()));
		int maxThreads = Integer.parseInt(System.getProperty(StartupSetting.MAX_THREADS.getKey()));
		String keystorePath = null;
		String keystore_password = null;
		Set<String> vpn_ids = new HashSet<String>();
		List<IPFilter> whitelist = new ArrayList<IPFilter>();
		List<IPFilter> blacklist = new ArrayList<IPFilter>();

		String gangliaHost = System.getProperty(StartupSetting.GANGLIA_HOST.getKey());
		int gangliaPort = gangliaHost != null ? Integer.parseInt(System.getProperty(StartupSetting.GANGLIA_PORT.getKey())) : -1;

		try {
			LogManager.getLogManager().readConfiguration(new FileInputStream("logging.properties"));
			System.out.println("read log configuration");
		} catch (Exception e) {
			System.err.println("error reading log config: " + e.toString());
		}

		if (System.getProperty(StartupSetting.IP_BLACKLIST.getKey()) != null) {
			try {
				blacklist = parseIPList(System.getProperty(StartupSetting.IP_BLACKLIST.getKey()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (System.getProperty(StartupSetting.IP_WHITELIST.getKey()) != null) {
			try {
				whitelist = parseIPList(System.getProperty(StartupSetting.IP_WHITELIST.getKey()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		keystore_password = System.getProperty(StartupSetting.KEYSTORE_PASSWORD.getKey());
		keystorePath = System.getProperty(StartupSetting.SSL.getKey());

		if (System.getProperty(StartupSetting.INFRASTRUCTURE_PEERS.getKey()) != null) {
			try {
				vpn_ids = read_vpn_keys(System.getProperty(StartupSetting.INFRASTRUCTURE_PEERS.getKey()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		CommunityDAO.get().registerVPN(vpn_ids);

		if (keystore_password != null) {
			System.setProperty("jetty.ssl.keypassword", keystore_password);
			System.setProperty("jetty.ssl.password", keystore_password);
		}

		/**
		 * Show the HTTPS URL to the user, if using this
		 */
		if (keystorePath != null) {
			try {
				KeyStore ks = KeyStore.getInstance("JKS");
				ks.load(new FileInputStream(keystorePath), keystore_password.toCharArray());
				String alias;
				/**
				 * If it has the default, use it. Otherwise, just use the first
				 * one.
				 */
				if (ks.containsAlias("community")) {
					alias = "community";
				} else {
					alias = ks.aliases().nextElement();
				}
				logger.info("Using alias: " + alias);

				MessageDigest digest = MessageDigest.getInstance("SHA-1");
				digest.update(ks.getCertificate(alias).getEncoded());
				String encodedBase64Hash = URLEncoder.encode(Base64.encode(digest.digest()), "UTF-8");
				String oururl = "https://" + (host == null ? "127.0.0.1" : host) + ((port != 443) ? (":" + port) : "") + "/?certhash=" + encodedBase64Hash;
				CommunityDAO.get().setURL(oururl);
				logger.info("SSL url with hash\n\n" + oururl + "\n\n");

			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		} else {
			String ourUrl = "http://" + (host == null ? "127.0.0.1" : host) + ((port != 80) ? (":" + port) : "") ;
			CommunityDAO.get().setURL(ourUrl);
		}
		if (gangliaHost != null) {
			startStatCollector(gangliaHost, gangliaPort);
		}
		(new EmbeddedServer(host, port, maxThreads, keystorePath, whitelist, blacklist)).start();

		if( System.getProperty(StartupSetting.UNENCRYPTED_PORT.getKey()) != null && 
				keystorePath != null ) {
			int alt_port = Integer.parseInt(System.getProperty(StartupSetting.UNENCRYPTED_PORT.getKey()));
			
			logger.info("Starting non-ssl server on port: " + alt_port);
			
			(new EmbeddedServer(host, alt_port, maxThreads, null, whitelist, blacklist)).start();
		}
		
		try { 
			while( true ) { 
				Thread.sleep(1000);
			}
		} catch( Exception e ) {}
		
	}

	private static void startStatCollector(String host, int port) {
		logger.fine("Starting stats collector: " + host + ":" + port);
		GangliaStat statCollector = new GangliaStat(host, port, 30, 60);
		statCollector.addMetric(new StatReporter("os_cs_keys_registered", "keys") {
			@Override
			public double getValue() {
				int length = CommunityDAO.get().getRegisteredKeys().length;
				logger.finest("Stats collector: reg users=" + length);
				return length;
			}
		});

		statCollector.addMetric(new StatReporter("os_cs_users_online", "users") {
			@Override
			public double getValue() {
				/*
				 * users are online for 2x the refresh limit + 60 seconds
				 */
				long online_limit = Long.parseLong(System.getProperty(Setting.REFRESH_INTERVAL.getKey())) * 60 * 1000 + 60 * 1000;
				int onlineUsers = 0;
				KeyRegistrationRecord[] registeredKeys = CommunityDAO.get().getRegisteredKeys();
				for (KeyRegistrationRecord rec : registeredKeys) {
					if ((System.currentTimeMillis() - rec.getLastRefreshedDate().getTime()) < online_limit) {
						onlineUsers++;
					}
				}
				logger.finest("Stats collector: online users=" + onlineUsers);
				return onlineUsers;
			}
		});

		statCollector.addMetric(new StatReporter("os_cs_ram_used", "Bytes") {
			@Override
			public double getValue() {
				Runtime runtime = Runtime.getRuntime();
				System.gc();
				System.gc();
				long mem_used = runtime.totalMemory() - runtime.freeMemory();
				logger.finest("Stats collector: mem used=" + mem_used);
				return mem_used;
			}
		});

	}

	private static List<IPFilter> parseIPList(String path) throws IOException {
		List<IPFilter> outList = new ArrayList<IPFilter>();

		BufferedReader in = new BufferedReader(new FileReader(path));
		while (in.ready()) {
			String line = in.readLine();
			if( line != null ) {
				outList.add(new IPFilter(line));
			} else { 
				break;
			}
		}

		return outList;
	}

	private static Set<String> read_vpn_keys(String path) throws IOException {
		Set<String> out = new HashSet<String>();
		BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
		while (in.ready()) {
			out.add(in.readLine());
		}
		return out;
	}

	public static final void usage() {
		System.out.println("EmbeddedServer: <config file [community.conf]>\n");
		System.exit(0);
	}
}
