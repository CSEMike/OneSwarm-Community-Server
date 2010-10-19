package edu.washington.cs.oneswarm.community2.server;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.core3.util.BEncoder;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.SHA1Hasher;
import org.mortbay.util.TypeUtil;

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.community2.shared.DuplicateAccountException;
import edu.washington.cs.oneswarm.community2.shared.KeyRegistrationRecord;
import edu.washington.cs.oneswarm.community2.shared.NoSuchUserException;
import edu.washington.cs.oneswarm.community2.test.CryptoUtils;
import edu.washington.cs.oneswarm.community2.utils.MutableLong;

/**
 * This class manages the linkage between soft state and the persistent storage of community 
 * server info. 
 * 
 * While it may appear that we try to achieve consistency between on-disk and in-memory state, 
 * don't be fooled. While we try to maintain 
 * consistency, it's never going to happen. The problem is that there may be 
 * several in-flight requests that have just been issued challenges. Also, we 
 * might have lazy updates, etc. that pruning of old entries will obviate. 
 * 
 * Instead of introducing a lot of locks and trying to make everything consistent, 
 * we're just going to accept that state may be inconsistent at times and fail gracefully. 
 * Just returning nothing to users is okay -- a missed update isn't the end of the world. 
 */

public final class CommunityDAO {
	
	public static final String EXPIRATION_PROPERTY = EmbeddedServer.Setting.KEY_EXPIRATION_SECONDS.getKey();
	
	private static Logger logger = Logger.getLogger(CommunityDAO.class.getName());

	BoneCP connectionPool = null;
	
	private List<KeyRegistrationRecord> peers = Collections.synchronizedList(new ArrayList<KeyRegistrationRecord>());
	private Map<String, KeyRegistrationRecord> key_to_record = new ConcurrentHashMap<String, KeyRegistrationRecord>();
	private Map<Long, KeyRegistrationRecord> id_to_record = new ConcurrentHashMap<Long, KeyRegistrationRecord>();
	private Map<KeyRegistrationRecord, Set<KeyRegistrationRecord>> topology = new ConcurrentHashMap<KeyRegistrationRecord, Set<KeyRegistrationRecord>>();
	private Map<String, Integer> ips_to_key_counts = Collections.synchronizedMap(new HashMap<String, Integer>());
	
	private long userTimeout;
	
	private String DRIVER = "com.mysql.jdbc.Driver";

	private Set<String> mVPN_ids = new HashSet<String>();
	
	private LinkedBlockingQueue<Runnable> mLazyDBQueue = new LinkedBlockingQueue<Runnable>();
	private String mURL;
	
	public enum UserRole { 
		ADMIN("admin", 1), 
		USER("user", 2), 
		MODERATOR("moderator", 3), 
		BOZO("bozo", 4);
		
		private String tag;
		private int id;

		private UserRole( String tag, int id ) { 
			this.tag = tag;
			this.id = id;
		}
		public String getTag() { return tag; } 
		public int getID() { return id; }
		
		public static UserRole roleForString( String inString ) { 
			for( UserRole r : values() ) { 
				if( r.getTag().equals(inString) ) { 
					return r;
				}
			}
			return null;
		}
		
		public static UserRole roleForID( int inID ) {
			for( UserRole r : values() ) {
				if( r.getID() == inID ) {
					return r;
				}
			}
			return null;
		}
	};
	
	private static final String [] TABLES = new String[] {
		"user_roles", 
		"roles", 
		"topology", 
		"banned_ips", 
		"swarm_extras", 
		"comments",
		"published_swarms",
		"categories", 
		"registered_keys", 
		"valid_accounts"
	};
	
	private static final String [] CREATE_TABLES = new String[]{
		
		"CREATE TABLE valid_accounts " +
		"(" +
		"	username VARCHAR(128) CHARSET utf8 NOT NULL UNIQUE KEY, " +
		"	password_hash CHAR(37), " + // hash is SHA1(username + password)
		"	registered_keys INTEGER DEFAULT 0, "	+ // how many keys have been registered by this identity?
		"	max_registrations INTEGER DEFAULT 5, " + // how many keys can this account register? 
				
		"	uid SERIAL PRIMARY KEY, " +
		
		"	CONSTRAINT unique_username UNIQUE(username) " + 
		") TYPE=INNODB",
		
		"CREATE TABLE roles " + 
		"( " +
		"	role_id SERIAL PRIMARY KEY, " +
		"	role VARCHAR(32)" +
		") TYPE=INNODB",
		
		"CREATE TABLE user_roles " +
		"(" +
		"	uid BIGINT UNSIGNED, " +
		"	role_id BIGINT UNSIGNED, " +
		"" +
		"	FOREIGN KEY(role_id) REFERENCES roles(role_id) ON DELETE CASCADE, " +
		" 	PRIMARY KEY(uid, role_id)" + 
		") TYPE=INNODB", 
		
		"CREATE TABLE registered_keys " +
		"( " +
		"	public_key VARCHAR(4096) CHARSET ascii NOT NULL, " + 
		"	key_hash INTEGER, " + 
		
		"	created_by_account BIGINT UNSIGNED, " +
		
		"	nick VARCHAR(256) CHARSET utf8 NOT NULL, " + // length needs to be at least CommunityConstants.MAX_NICK_LENGTH
		
		"	registration_timestamp TIMESTAMP DEFAULT NOW(), " +
		"	last_refresh_timestamp TIMESTAMP DEFAULT '1980-10-10 10:00:00', " + // overcome the only one DEFAULT NOW issue, and need a valid timestamp here. 
		
		"	registration_ip VARCHAR(16) NOT NULL, " + 
		
		"	db_id SERIAL PRIMARY KEY, " +
		
		"	CONSTRAINT UniqueKey UNIQUE(public_key(767)), " + 
		
		"	FOREIGN KEY(created_by_account) REFERENCES valid_accounts(uid) ON DELETE CASCADE " + 
		") TYPE=INNODB", 
		
		// to support efficient range queries on the hash index
		"CREATE INDEX hash_index ON registered_keys(key_hash)",
		
		"CREATE TRIGGER registration_account_increment AFTER INSERT ON registered_keys \n" +  
		"FOR EACH ROW \n" + 
		"UPDATE valid_accounts SET registered_keys = registered_keys + 1 WHERE valid_accounts.uid = NEW.created_by_account ",   
		
		"CREATE TRIGGER registration_account_decrement AFTER DELETE ON registered_keys \n" +  
		"FOR EACH ROW \n" + 
		"UPDATE valid_accounts SET registered_keys = registered_keys - 1 WHERE valid_accounts.uid = OLD.created_by_account ", 
		
		"CREATE TABLE topology " +
		"(" +
		"	A_id	BIGINT UNSIGNED NOT NULL, \n" +
		"	B_id	BIGINT UNSIGNED NOT NULL, \n" +
		"	created_timestamp TIMESTAMP DEFAULT NOW(), \n" +
		"" +
		"	PRIMARY KEY(A_id, B_id), \n" +
		"" +
		"	CONSTRAINT symmetry_inequality CHECK( A_id < B_id ), \n" +
		"	" +
		"	FOREIGN KEY(A_id) REFERENCES registered_keys(db_id) ON DELETE CASCADE, \n" +
		"	FOREIGN KEY(B_id) REFERENCES registered_keys(db_id) ON DELETE CASCADE \n" +
		") TYPE=INNODB", 
		
		"CREATE TABLE categories " +
		"(" +
		"	category VARCHAR(128) CHARSET utf8 NOT NULL PRIMARY KEY" +
		") TYPE=INNODB", 
		
		"CREATE TABLE published_swarms \n" +
		"(" +
		"	swarmid SERIAL PRIMARY KEY, \n" +
		"" +
		"	name VARCHAR(512) CHARSET utf8 NOT NULL, \n" +
		"" +
		"	num_files INTEGER, \n" +
		"	total_size BIGINT, \n" +
		"" +
		"	category VARCHAR(128) CHARSET utf8 DEFAULT NULL, " +
		"" +
		"	uploaded_by BIGINT UNSIGNED, \n" +
		"" +
		"	infohash CHAR(40), \n" +
		"	date_uploaded TIMESTAMP DEFAULT NOW(), \n" +
		"" +
		"	has_torrent BOOLEAN NOT NULL, \n" + 
		"	bin MEDIUMBLOB, \n" +
		"" + 
		"	removed BOOLEAN DEFAULT FALSE, \n" + 
		"	needs_moderated BOOLEAN DEFAULT TRUE, " +
		"" +
		"	ip VARCHAR(16) NOT NULL, \n" +
		"	FOREIGN KEY(uploaded_by) REFERENCES valid_accounts(uid) ON DELETE CASCADE, \n" +
		"	FOREIGN KEY(category) REFERENCES categories(category) ON DELETE SET NULL, " +
		
		"	CONSTRAINT unique_infohash UNIQUE(infohash) " +
		") TYPE=INNODB", 
		
		"CREATE INDEX uploaded_by_index ON published_swarms(uploaded_by)", 
		"CREATE INDEX infohash_index ON published_swarms(infohash)", 
		"CREATE INDEX name_index ON published_swarms(name)",
		"CREATE INDEX unmoderated_index ON published_swarms(needs_moderated)", 
		
		"CREATE TABLE banned_ips " +
		"( " +
		"	ip VARCHAR(16) NOT NULL PRIMARY KEY" +
		") TYPE=MyISAM", 
		
		"CREATE TABLE swarm_extras " +
		"( " +
		"	swarmid BIGINT UNSIGNED PRIMARY KEY, " +
		
		"	description TEXT CHARSET utf8, \n" +
		
		"	downloads INT UNSIGNED DEFAULT 0, " +
		"	language CHAR(10) DEFAULT NULL, " + // ISO 639-1 ?
		"" +
		"	upvotes INTEGER DEFAULT 0, " +
		"	downvotes INTEGER DEFAULT 0, " +
		"" +
		"	previewpng MEDIUMBLOB DEFAULT NULL, " +
		"" +
		"	FOREIGN KEY(swarmid) REFERENCES published_swarms(swarmid) ON DELETE CASCADE" +
		") TYPE=INNODB", 
		
		"CREATE TABLE comments " + 
		"( " +
		"	swarmid BIGINT UNSIGNED, " +
		"	commentid SERIAL PRIMARY KEY, " +
		"" +
		"	accountname VARCHAR(128) CHARSET utf8 NOT NULL, " + 
		"" +
		"	time TIMESTAMP DEFAULT NOW(), " +
		"" +
		"	reply_to BIGINT UNSIGNED DEFAULT NULL, " +
		"" +
		"	upvote INTEGER DEFAULT 0, " +
		"	downvote INTEGER DEFAULT 0, " +
		"" +
		"	ip	VARCHAR(16) NOT NULL, " +
		"" +
		"	body TEXT CHARSET utf8 NOT NULL, " +
		"" + 
		"	removed BOOLEAN DEFAULT FALSE, " + 
		"" +
		"	FOREIGN KEY(accountname) REFERENCES valid_accounts(username) ON DELETE CASCADE, " +
		"	FOREIGN KEY(swarmid) REFERENCES published_swarms(swarmid) ON DELETE CASCADE " +
		"	" +
		") TYPE=INNODB", 
		
		"CREATE INDEX comment_dates ON comments(time)"
	};
	
	private static CommunityDAO mInst = null;
	protected static final Principal SUPERUSER = new CommunityAccount("admin", null, new String[]{"admin"}, 0, 0, 0);
	private static final String[] DEFAULT_CATEGORIES = new String[]{"Other", "Video", "Audio", "Pictures", "Documents"};
	
	public synchronized static CommunityDAO get() { 
		if( mInst == null ) {
			mInst = new CommunityDAO();
		}
		return mInst;
	}
	
	public synchronized KeyRegistrationRecord [] getRegisteredKeys() { 
		return peers.toArray(new KeyRegistrationRecord[0]);
	}
	
	private CommunityDAO() {
		
		// Create the Derby DB
		try
		{
			Class.forName(DRIVER);
		} 
		catch( ClassNotFoundException e )
		{
			logger.severe(e.toString());
		}

		try
		{
			StringBuilder connect = new StringBuilder();
			connect.append("jdbc:mysql://");
			connect.append(System.getProperty("db.host"));
			connect.append(System.getProperty("db.port") == null ? ":3306" : (":"+System.getProperty("db.port")));
			connect.append("/"+(System.getProperty("db.name")==null ? "community_db" : System.getProperty("db.name")));
//			connect.append("?user=" + System.getProperty("db.user"));
//			connect.append("&password=" + System.getProperty("db.password"));
			connect.append("?characterEncoding=UTF8&characterSetResults=UTF8&useUnicode=true");
			
			logger.finest("DB connect string: " + connect.toString());
//			con = DriverManager.getConnection(connect.toString());
			
			BoneCPConfig config = new BoneCPConfig();
			config.setJdbcUrl(connect.toString());
			
			config.setUsername(System.getProperty("db.user"));
			config.setPassword(System.getProperty("db.password"));
			
			config.setMinConnectionsPerPartition(5);
			config.setMaxConnectionsPerPartition(30);
			config.setPartitionCount(1);
			connectionPool = new BoneCP(config); 
		}
		catch( SQLException e )
		{
			logger.severe(e.toString());
			e.printStackTrace();
		}
		
//		drop_tables();
		check_create_tables();
		load();
		
		/**
		 * Remove old peers every hour. 
		 */
		(new Timer("prune old peers", true)).schedule(new TimerTask(){
			public void run() {
				
				if( System.getProperty(EXPIRATION_PROPERTY) != null ) {
					try {
						userTimeout = Long.parseLong(System.getProperty(EXPIRATION_PROPERTY)) * 1000;
						logger.info("Using user timeout: " + userTimeout + " seconds");
					} catch( Exception e ) {
						logger.warning("Invalid user expiration timeout: " + System.getProperty(EXPIRATION_PROPERTY));
					}
				}
				
				int removedDB = (new SQLStatementProcessor<Integer>("DELETE FROM registered_keys WHERE last_refresh_timestamp < ?") {
					Integer process(PreparedStatement s) throws SQLException {
						s.setTimestamp(1, new java.sql.Timestamp(System.currentTimeMillis() - userTimeout));
						int removed = s.executeUpdate();
						return removed;
					}
				}).doit();
				
				logger.info("Pruned " + removedDB + " old peers");
				
				load();
			}}, 1000, 15 * 60 * 1000);
		
		/**
		 * Drain the lazy DB queue
		 */
		Thread lazyDrain = new Thread("Lazy DB drainer") {
			public void run() {
				while( true ) {
					try {
						mLazyDBQueue.take().run();
					}catch( Exception e ) {
						e.printStackTrace();
						try {
							/**
							 * Slow down if bad things are happening
							 */
							Thread.sleep(100);
						} catch (InterruptedException e1) {}
					}
				}
			}
		};
		lazyDrain.setDaemon(true);
		lazyDrain.start();
	}

	private void check_create_tables() {
		
		List<String> tables = (new SQLStatementProcessor<List<String>>("SHOW TABLES") {
			List<String> process(PreparedStatement s) throws SQLException { 
				ResultSet rs = s.executeQuery();
				List<String> out = new ArrayList<String>();
				while( rs.next() ) { 
					out.add(rs.getString(1));
				}
				return out;
			}
		}).doit();
		
		if( tables.size() == 0 ) { 
			logger.info("Creating DB schema...");
			create_tables();
		} else if( tables.size() != TABLES.length ) { 
			logger.warning("DB schema seems out of date. Trying to recreate tables. (This may or may not work)");
			create_tables();
		}
		
	}

	/**
	 * (Re)load the database and update the soft state once completed. 
	 */
	private void load() {
		
		long startTime = System.currentTimeMillis();
		logger.info("Starting reload of soft state...");
		
		// the next version of the soft state
		final Map<KeyRegistrationRecord, Set<KeyRegistrationRecord>> topology = new ConcurrentHashMap<KeyRegistrationRecord, Set<KeyRegistrationRecord>>();
		final Map<String, KeyRegistrationRecord> key_to_record = new ConcurrentHashMap<String, KeyRegistrationRecord>();
		final Map<Long, KeyRegistrationRecord> id_to_record = new ConcurrentHashMap<Long, KeyRegistrationRecord>();
		final List<KeyRegistrationRecord> peers = Collections.synchronizedList(new ArrayList<KeyRegistrationRecord>());
		final Map<String, Integer> ips_to_key_counts = Collections.synchronizedMap(new HashMap<String, Integer>());
		
		(new SQLStatementProcessor<Void>("SELECT public_key, nick, created_by_account, registration_ip, registration_timestamp, last_refresh_timestamp, db_id FROM registered_keys ORDER BY key_hash ASC") {
			Void process(PreparedStatement s) throws SQLException {
				
				ResultSet rs = s.executeQuery();
				
				while( rs.next() ) {
					KeyRegistrationRecord fr = new KeyRegistrationRecord( rs.getString("public_key"), rs.getString("nick"), new Date(rs.getTimestamp("registration_timestamp").getTime()), 
							new Date(rs.getTimestamp("last_refresh_timestamp").getTime()), rs.getString("registration_ip"), 
							rs.getLong("created_by_account"), rs.getLong("db_id") );
					
					peers.add(fr);
					key_to_record.put(fr.getBase64PublicKey(), fr);
					id_to_record.put(fr.getID(), fr);
					topology.put(fr, Collections.synchronizedSet(new HashSet<KeyRegistrationRecord>()));
				}
				
				return null;
			}
		}).doit();
		
		(new SQLStatementProcessor<Void>("SELECT registration_ip, COUNT(*) FROM registered_keys GROUP BY registration_ip") {
			Void process(PreparedStatement s) throws SQLException {
				
				ResultSet rs = s.executeQuery();
				while( rs.next() ) {
					String reg_ip = rs.getString(1);
					Integer count = rs.getInt(2);
					
					ips_to_key_counts.put(reg_ip, count);
					
					logger.finest("ip: " + reg_ip + " registered " + count);
				}
				
				return null;
			}
		}).doit();
		
//		(new SQLStatementProcessor<Void>("SELECT username, registered_keys FROM valid_accounts") {
//			Void process(PreparedStatement s) throws SQLException {
//				ResultSet rs = s.executeQuery();
//				while( rs.next() ) {
//					String reg_username = rs.getString(1);
//					Integer count = rs.getInt(2);
//					
//					logger.finest("user: " + reg_username + " registered " + count);
//				}
//				return null;
//			}
//		}).doit();
		
		(new SQLStatementProcessor<Void>("SELECT * FROM topology") {
			Void process(PreparedStatement s) throws SQLException {
				ResultSet rs = s.executeQuery();
				while( rs.next() ) {
					KeyRegistrationRecord a = id_to_record.get(rs.getLong("A_id"));
					KeyRegistrationRecord b = id_to_record.get(rs.getLong("B_id"));
					
					if( a == null ) { 
						logger.severe("Null soft state key registration record for ID: " + rs.getLong("A_id"));
						continue;
					}
					
					if( b == null ) { 
						logger.severe("Null soft state key registration record for ID: " + rs.getLong("B_id"));	
					}
					
					createFriendLink(topology, a, b, false);
				}
				return null;
			}
		}).doit();
		
		synchronized(CommunityDAO.this) {
			CommunityDAO.this.key_to_record = key_to_record;
			CommunityDAO.this.id_to_record = id_to_record;
			CommunityDAO.this.topology = topology;
			CommunityDAO.this.peers = peers;
			CommunityDAO.this.ips_to_key_counts = ips_to_key_counts;
		}
		
		logger.info("db sync took: " + (System.currentTimeMillis() - startTime) + " for " + peers.size());
	}
	
	private synchronized void createFriendLink( final Map<KeyRegistrationRecord, Set<KeyRegistrationRecord>> topology, final KeyRegistrationRecord a, final KeyRegistrationRecord b, boolean writeToDB ) {
		Set<KeyRegistrationRecord> a_peers = topology.get(a);
		Set<KeyRegistrationRecord> b_peers = topology.get(b);
		
		a_peers.add(b);
		b_peers.add(a);
		
		if( writeToDB ) {
			/**
			 * Might these db_ids no longer be valid by the time we actually get around to writing this update? 
			 * Sure -- but that's okay, the referential integrity checks will cause a rollback, we'll print an 
			 * error message that can be ignored, and the next time this client refreshes, it will get the correct entries. 
			 */
			try {
				mLazyDBQueue.put(new Runnable(){
					public void run() {
						(new SQLStatementProcessor<Void>("INSERT INTO topology (A_id, B_id) VALUES (?, ?)") { 
							Void process( PreparedStatement s ) throws SQLException {
								if( a.getID() < b.getID() ) {
									s.setLong(1, a.getID());
									s.setLong(2, b.getID());
								} else { 
									s.setLong(1, b.getID());
									s.setLong(2, a.getID());
								}
								s.executeUpdate();
								return null;
							}
						}).doit();
					}
				});
			} catch (InterruptedException e) {
				e.printStackTrace();
				logger.warning(e.toString());
			}
		}
	}
	
	public synchronized void drop_tables() {

		Connection c = null;
		try {
			c = connectionPool.getConnection();
						
			clearSoftState();

			Statement stmt = c.createStatement();
			for( String t : TABLES )
			{
				try {
					stmt.executeUpdate("DROP TABLE IF EXISTS " + t);
				} catch( Exception e ) {
					logger.warning(e.toString());
				}
			}
			stmt.close();
		} catch( Exception e ) {}
		finally {
			try {
				c.close();
			} catch( SQLException e ) {}
		}
	}
	
	public synchronized void create_tables() 
	{
		Connection c = null;
		try 
		{		
			clearSoftState();

			c = connectionPool.getConnection();
			Statement s = c.createStatement();
			
			s.executeQuery("show tables");
			
			for( String t : CREATE_TABLES )
			{
				try {
					s.execute(t);
				} catch( Exception e ) {
//					e.printStackTrace();
					logger.warning(e.toString());
					logger.warning(t);
				}
			}
			s.close();
			
			/**
			 * Create roles
			 */
			PreparedStatement stmt = null;
			for( UserRole role : UserRole.values() ) {
				stmt = c.prepareStatement("INSERT INTO roles (role) VALUES ('" + role.getTag() + "')");
				stmt.executeUpdate();
				stmt.close();
			}
			
		} catch( SQLException e ) {
			e.printStackTrace();
		} finally {
			try{
				c.close();
			} catch( SQLException e ) {}
		}
		
		try { 
			createAccount("admin", "", UserRole.ADMIN);
			logger.info("Created admin account with blank password -- change the password!");
		} catch( DuplicateAccountException e ) {
			logger.info("Admin account already exists, skipped creation");
		} catch( IOException e ) {
			e.printStackTrace();
			logger.warning(e.toString());
		}
		
		for( String cat : DEFAULT_CATEGORIES ) {
			addCategory(cat);
		}
	}
	
	public void addCategory( final String category ) {
		(new SQLStatementProcessor<Void>("INSERT INTO categories (category) VALUES (?)") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setString(1, category);
				s.executeUpdate();
				return null;
			}}).doit();
	}

	private synchronized void clearSoftState() {
		peers.clear();
		key_to_record.clear();
		id_to_record.clear();
		ips_to_key_counts.clear();
		topology.clear();
	}
	
	public synchronized Set<KeyRegistrationRecord> getCurrentRandomPeers( KeyRegistrationRecord inFriend ) {
		return topology.get(inFriend);
	}

	public List<KeyRegistrationRecord> getRandomPeers( final String inBase64Key, final int inDesired, final int inMax, boolean updateRefreshTime ) {
		
		if( isRegistered(inBase64Key) == false ) {
			return null;
		}
		
		if( updateRefreshTime ) {
			updateRefreshTime(inBase64Key);
		}
		
		/**
		 * First retrieve any existing friends. 
		 */
		KeyRegistrationRecord me = key_to_record.get(inBase64Key);
		logger.finer("Get random peers for: " + me.getNickname());
		
		Set<KeyRegistrationRecord> friends = topology.get(me);
		logger.finer("\t" + friends.size() + " existing random peers in topo");
		
		
		/**
		 * Peering policy is to add friends on request until exceeding Max(85% of inNumber, 5). 
		 * The goal here is to avoid having strongly connected components that cannot be broken into by new peers, so we 
		 * always keep a few slots open for the most recent joiner (churn introduces some mixing as well) 
		 */
		long start = System.currentTimeMillis();
		KeyRegistrationRecord [] candidates = peers.toArray(new KeyRegistrationRecord[0]);
		Collections.shuffle(Arrays.asList(candidates));
		logger.finest("\tShuffled " + candidates.length + " candidates in " + (System.currentTimeMillis() - start));
		for( KeyRegistrationRecord candidate : candidates ) {
			// have we found enough?
			if( friends.size() > Math.max(0.85 * (double)inDesired, 5) ) {
				break;
			}
			
			// skip friends already in the set
			if( friends.contains(candidate) ) {
				continue;
			}
			
			// skip ourselves
			if( candidate.equals(me) ) {
				continue;
			}
			
			// candidate is already past the maximum number allowed
			if( topology.get(candidate).size() > inMax ) {
				logger.finest("\tCandidate: " + candidate.getNickname() + " has too many: " + topology.get(candidate).size());
				continue;
			} 
			
			/** 
			 * success -- add to output and store in DB. Since we're returning the topology list directly, we 
			 * don't need an extra copy here
			 */
			logger.finest("\tAdding candidate: " + candidate.getNickname() + " with " + topology.get(candidate).size());
			createFriendLink(topology, me, candidate, true);
		}
		
		logger.finer("Returning " + friends.size() + " for " + me.getNickname());
		return Arrays.asList(friends.toArray(new KeyRegistrationRecord[0]));
	}
	
	/**
	 * This method creates a simple ring topology. This has the unfortunate property that nearby peers have significant overlap 
	 * in peer lists, increasing average path length, which is why we're now using random matching in the default case. 
	 * 
	 * For VPN matching, however, dividing up a circular keyspace is much more desirable since we don't need to 
	 * store anything in the DB and the adjacency overlap problem really doesn't apply, so we're keeping this code 
	 * around in support of that. 
	 * 
	 * @param inBase64Key the requesting key
	 * @param inNumber the number of output friends
	 * @param updateRefreshTime should we update the peer's last refreshed time?
	 * @return a list of nearby FriendRecords
	 */
	public synchronized List<KeyRegistrationRecord> getNearestPeers( final String inBase64Key, final int inNumber, boolean updateRefreshTime ) {
		
		if( isRegistered(inBase64Key) == false ) {
			return null;
		}
		
		if( updateRefreshTime ) {
			updateRefreshTime(inBase64Key);
		}
		
		Set<KeyRegistrationRecord> out = new HashSet<KeyRegistrationRecord>();
		
		int ourIndex = Collections.binarySearch(peers, new KeyRegistrationRecord(inBase64Key));
		if( ourIndex < 0 ) {
			System.err.println("Inconsist DB/memory state wrt peer: " + inBase64Key);
			return null;
		}
		
		for( int i=1; i<=inNumber/2; i++ ) {
			int plus = (ourIndex+i) % peers.size();
			if( plus != ourIndex ) {
				out.add(peers.get(plus));
			}
			
			int minus = (ourIndex-i) % peers.size();
			if( minus != ourIndex ) {	
				out.add(peers.get(minus >= 0 ? minus : (peers.size() - 1 + minus)));
			}
		}
		
		return Arrays.asList(out.toArray(new KeyRegistrationRecord[0]));
	}

	private void updateRefreshTime(final String inBase64Key) {
		try {
			/**
			 * Might this client no longer exist by the time this update is attempted? Sure, but that's okay. Aside from a 
			 * scary error message, no harm done. 
			 */
			mLazyDBQueue.put(new Runnable() {
				public void run() {
					(new SQLStatementProcessor<Void>("UPDATE registered_keys SET last_refresh_timestamp = CURRENT_TIMESTAMP WHERE public_key = ?") {
						public Void process( PreparedStatement s ) throws SQLException {
							s.setString(1, inBase64Key);
							if( s.executeUpdate() != 1 ) {
								logger.warning("Couldn't update last_refresh_timestamp for: " + inBase64Key + " not (or no longer) in DB");
							}
							else {
								KeyRegistrationRecord rec = key_to_record.get(inBase64Key);
								if( rec == null ) {
									logger.warning("Inconsistent DB/cache state in updateRefreshTime() for key: " + inBase64Key);
								} 	
								else {
									rec.setLastRefresh( new Date() );
								}
							}
							
							return null;
						}
					}).doit();
				}
			});
			
			KeyRegistrationRecord rec = key_to_record.get(inBase64Key);
			if( rec == null ) {
				logger.warning("Got update refresh time for a key that wasn't in soft state: " + inBase64Key);
			} else {
				rec.setLastRefresh(new java.util.Date());
			}
		} catch( InterruptedException e ) {
			e.printStackTrace();
			logger.warning(e.toString());
		}
	}
	
	private synchronized List<KeyRegistrationRecord> getVPNList(final String inBase64Key) {
		
		Set<KeyRegistrationRecord> out = new HashSet<KeyRegistrationRecord>();
		
		/**
		 * If this key is an infrastructure peer, the nearest (total clients / 1/2total servers). 
		 * Otherwise, return just the 
		 * registered infrastructure peers. 
		 */
		for( String infraKey : mVPN_ids ) {
			if( infraKey.equals(inBase64Key) == false &&
				key_to_record.containsKey(infraKey) ) 
			{
				out.add(key_to_record.get(infraKey));
				logger.finest("Adding infrastructure peer " + key_to_record.get(infraKey).getNickname() + " / " + infraKey);
			} else { 
				logger.finest("Skipping own or unregistered infrastructure key: " + infraKey);
			}
		}
		
		logger.finest("Added " + out.size() + " infrastructure peers to the VPN-response for: " + inBase64Key);
		updateRefreshTime(inBase64Key);
		
		/**
		 * Unlike ordinary operation, we want _all_ the peers to be covered. 
		 * Divide things up so that each registered peer is covered by ~2 infrastructure peers 
		 */
		if( mVPN_ids.contains(inBase64Key) ) {
			int number = key_to_record.size() / (int)Math.round(0.5 * mVPN_ids.size());
			logger.finest("For infrastructure peer, giving: " + number + " / " + key_to_record.size() + " -- " + inBase64Key);
			
			/**
			 * We've already updated the refresh time above
			 */
			out.addAll(getNearestPeers(inBase64Key, number, false));
		}
		
		return Arrays.asList(out.toArray(new KeyRegistrationRecord[0]));
	}
	
	// for debugging
	private void bench() {
		String k = key_to_record.keySet().toArray(new String[0])[0];
		long start = System.currentTimeMillis();
		for( int i=0; i<3000; i++ ) {
			isRegistered(k);
//			key_to_record.containsKey(k);
		}
		System.out.println("took: " + (System.currentTimeMillis()-start));
	}
	
	public static void main( String[] args ) throws Exception
	{
		EmbeddedServer.load_config("community.conf");
		
		CommunityDAO rep = CommunityDAO.get();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		
		if( args.length > 0 ) { 
			if( args[0].equals("-dump") ) {
				PrintStream out = new PrintStream(new FileOutputStream(args[1]));
				for(String t : new String[]{"keys", "valid_accounts"}) {
					out.println(t);
					rep.dumpTable(t, out, "\n");
				}
			}
		}
		
		while( true )
		{
			String line;			

			System.out.print( "\n> " );
			System.out.flush();
			line = in.readLine();
			String [] toks = line.split("\\s+");
			
			try
			{
				if( line.equals("create") )
				{
					rep.create_tables();
				}
				else if( line.equals("bench") ) {
					
					rep.bench();
					
				} else if( line.startsWith("drop") ) {
					rep.drop_tables();
				}
				else if( line.startsWith("show") )
				{
					rep.dumpTable(toks[1], System.out, "\n");
				}
				else if( line.equals("newaccount") ) {
					if( toks.length != 4 ) {
						System.err.println("newaccount <user> <pass> <role>");
						continue;
					}
					try {
						CommunityDAO.get().createAccount(toks[1], toks[2], UserRole.roleForString(toks[3]));
					} catch( Exception e ) {
						e.printStackTrace();
					}
				}
				else if( line.equals("test") )
				{
					rep.drop_tables();
					rep.create_tables();
					CryptoUtils c = new CryptoUtils();
					long start = System.currentTimeMillis();
					for( int i=0; i<100; i++ ) {
						String kname = Base64.encode(c.getPair().getPublic().getEncoded()).replaceAll("\n","");
						rep.registerUser(kname, "user123-" + kname.substring(40, 45), "1.2.3.4", "admin");	
					}
				}
				else if( line.equals("q") )
					break;
				else
				{
					Connection con = rep.connectionPool.getConnection();
					Statement s = con.createStatement();
					if( line.toLowerCase().startsWith("select") )
					{
						int count =0;
						ResultSet rs = s.executeQuery(line);
						while( rs.next() )
							count++;
						System.out.println("count: " + count);
					}
					else
						System.out.println( s.execute(line) + "" );
					s.close();

					con.close();
				}
			}
			catch( SQLException e )
			{
				System.err.println(e);
				e.printStackTrace();
			}
		}
	}
	
	public void dumpTable(final String table, final PrintStream inOut, final String newline) {
	
		(new SQLStatementProcessor<Void>("SELECT * FROM " + table) { 
			Void process( PreparedStatement s ) throws SQLException {
				ResultSet rs = s.executeQuery();
				ResultSetMetaData md = rs.getMetaData();
				
				for( int i=1; i<=md.getColumnCount(); i++ )
				{
					inOut.print( md.getColumnLabel(i) + " " );
				}
				inOut.print(newline);
				
				PrintWriter out = new PrintWriter(new OutputStreamWriter(inOut));
				
				int rowCount=0;
				while( rs.next() )
				{
					rowCount++;
					for( int i=1; i<=md.getColumnCount(); i++ )
					{
						out.printf( "%" + md.getColumnLabel(i).length() + "s ", rs.getObject(i) == null ? "null" : rs.getObject(i).toString() );
						out.flush();
					}
					out.flush();
					out.print(newline);
				}
				out.print("-- " + rowCount + " total --" + newline);
				out.flush();
				return null;
			}
		}).doit();
	}

	/**
	 * To replace boilerplate SQL code. 
	 */
	abstract class SQLStatementProcessor<V> {
		String mSQL;
		protected Connection con = null;
		
		public SQLStatementProcessor( String sql ) {
			mSQL = sql;
		}
		
		public V doit() {
			PreparedStatement stmt = null;
			V out = null;
			con = null;
			try {
				con = connectionPool.getConnection();
				
				stmt = con.prepareStatement(mSQL);
				out = process(stmt);
				
			} catch( SQLException e ) {
				e.printStackTrace();
				logger.warning(e.toString());
			}
			finally {
				try {
					stmt.close();
				} catch( SQLException e ) {
					e.printStackTrace();
					logger.warning(e.toString());
				}
				try {
					con.close();
				} catch( SQLException e ) {
					e.printStackTrace();
					logger.warning(e.toString());
				}
			}
			return out;
		}
		
		abstract V process(PreparedStatement s) throws SQLException;
	}

	public synchronized void registerUser(final String key, final String nick, final String remote_ip, final String username) throws DuplicateRegistrationException, TooManyRegistrationsException {
		
		if( isRegistered(key) ) {
			throw new DuplicateRegistrationException(key);
		}
		
		logger.finest("Registration request: u: " + username + " ip: " + remote_ip + " n: " + nick);
		
		int maxRegistrationsPerIP = 5; 
		try { 
			maxRegistrationsPerIP = Integer.parseInt(System.getProperty(EmbeddedServer.Setting.KEY_REG_LIMIT_IP.getKey()));
		} catch( Exception e ) { 
			e.printStackTrace();
		}
		
		/**
		 * "admin" is used to credit registrations on open servers and has no limit -- but, we do
		 * enforce a limit on the number of registrations per-IP so that people can't 
		 * crawl the server's entire membership. 
		 */
		final MutableLong user_id = new MutableLong(-1); // something sure to fail in case there is some intermediate SQL error
		if( username.equals("admin") ) {
			user_id.set(1);
			if( ips_to_key_counts.containsKey(remote_ip) ) {
				if( ips_to_key_counts.get(remote_ip) > maxRegistrationsPerIP && 
					remote_ip.equals("127.0.0.1") == false ) { // unlimited registrations from localhost 
					throw new TooManyRegistrationsException(ips_to_key_counts .get(remote_ip));
				}
			}
		} 
		/**
		 * else this is an authenticated server and we have per-username registration limits to enforce 
		 */
		else {
			boolean too_many_per_account = (new SQLStatementProcessor<Boolean>("SELECT registered_keys, max_registrations, uid FROM valid_accounts WHERE username = ?") {
				Boolean process( PreparedStatement s ) throws SQLException {
					s.setString(1, username);
					ResultSet rs = s.executeQuery();
					if( rs.next() == false ) {
						throw new SQLException("No valid_accounts record for username: " + username + " and should have been detected earlier.");
					}
					user_id.set((int)rs.getLong("uid"));
					logger.finer("Checking max registrations for " + username + " " + rs.getInt("registered_keys") + " / " + rs.getInt("max_registrations"));
					return rs.getInt("registered_keys") + 1 > rs.getInt("max_registrations");
				}
			}).doit();
			
			if( too_many_per_account ) {
				throw new TooManyRegistrationsException();
			}
		}
		
		(new SQLStatementProcessor<Void>("INSERT INTO registered_keys (public_key, registration_ip, key_hash, nick, created_by_account) VALUES (?, ?, ?, ?, ?)") {
			Void process( PreparedStatement s ) throws SQLException {
				s.setString(1, key);
				s.setString(2, remote_ip);
				s.setInt(3, key.hashCode());
				s.setString(4, nick);
				s.setLong(5, user_id.get());
				s.executeUpdate();
				return null;
			}
		}).doit();
		
		Long new_id = (new SQLStatementProcessor<Long>("SELECT db_id FROM registered_keys WHERE public_key = ?") {
			Long process( PreparedStatement s ) throws SQLException {
				s.setString(1, key);
				ResultSet rs = s.executeQuery();
				rs.next();
				return rs.getLong(1);
			}
		}).doit();
		
		/**
		 * Update all the soft state. 
		 */
		KeyRegistrationRecord neu = new KeyRegistrationRecord(key, nick, new Date(), new Date(), remote_ip, user_id.get(), new_id);
		int index = Collections.binarySearch(peers, neu) + 1;
		if( Math.abs(index) == peers.size() ) {
			peers.add(neu); 
		} else {
			peers.add(Math.abs(index), neu);
		}
		key_to_record.put(neu.getBase64PublicKey(), neu);
		id_to_record.put(neu.getID(), neu);
		topology.put(neu, new HashSet<KeyRegistrationRecord>());
		
		if( ips_to_key_counts.get(remote_ip) == null ) {
			ips_to_key_counts.put(remote_ip, 1);
		} else {
			ips_to_key_counts.put(remote_ip, ips_to_key_counts.get(remote_ip)+1);
		}
		
		// TODO: debug, remove me. 
//		for( int i=0; i<peers.size()-1; i++ ) {
//			if( peers.get(i).base64key.hashCode() > peers.get(i+1).base64key.hashCode() ) {
//				
//				for( FriendRecord f : peers ) {
//					System.err.println(f);
//				}
//				
//				throw new RuntimeException("not sorted @ " + i + " / " + peers.get(i).nickname);
//			}
//		}
	}
	
	public boolean isRegistered(final String key) {
		
		return key_to_record.containsKey(key);
		
//		return (new SQLStatementProcessor<Boolean>("SELECT db_id FROM registered_keys WHERE public_key = ?") {
//			Boolean process( PreparedStatement s ) throws SQLException {
//				s.setString(1, key);
//				return s.executeQuery().next();
//			}
//		}).doit();
	}
	
	private CommunityAccount accountFromResultSet( Connection con, ResultSet rs ) throws SQLException {
		
		long id = rs.getLong("uid");
		/**
		 * Grab roles from other table. Don't use the wrapper since this is only called 
		 * from process() (i.e., when we already have the lock)
		 */
		PreparedStatement s = con.prepareStatement("SELECT role_id FROM user_roles WHERE uid = ?");
		s.setLong(1, id);
		ResultSet roleSet = s.executeQuery();
		List<Long> roles = new ArrayList<Long>();
		while( roleSet.next() ) { 
			roles.add(roleSet.getLong(1));
		}
		s.close();
		
		String [] converted = new String[roles.size()];
		for( int i=0; i<converted.length; i++ ) { 
			// -1 corrects for SQL 1-based indexing
			converted[i] = UserRole.values()[roles.get(i).intValue()-1].getTag();
//			converted[i] = UserRole.roleForID(roles.get(i).intValue()).getTag();
		}
		
		CommunityAccount out = new CommunityAccount(rs.getString("username"), 
					rs.getString("password_hash"),
					converted, 
					rs.getInt("registered_keys"), 
					rs.getInt("max_registrations"), 
					rs.getLong("uid"));
		
		if( logger.isLoggable(Level.FINEST) ) {
			logger.finest("Converted account: " + out); 
		}
		
		return out;
	}

	public Principal authenticate(final String username, final String credentials) {
		
		logger.info("authenticate called for user: " + username );
		
		CommunityAccount purported = (new SQLStatementProcessor<CommunityAccount>("SELECT * FROM valid_accounts WHERE username = ?") {
			CommunityAccount process( PreparedStatement s ) throws SQLException {
				s.setString(1, username);
				ResultSet rs = s.executeQuery();
				if( rs.next() ) {
					return accountFromResultSet(con, rs);
				}
				return null;
			}
		}).doit();
		
		if( purported == null ) {
			logger.warning("Unknown user: " + username + " / returning null.");
			return null;
		}
		
		try {
			if( Arrays.equals(getPasswordHash(credentials.toString()), 
					TypeUtil.fromHexString(purported.getHash().substring("MD5:".length()))) ) {
				
				logger.finest("Passwords match for: " + username);
				
				return purported;
			}
		} catch (IOException e) {
			logger.warning(e.toString());
			e.printStackTrace();
		}
		
		logger.fine("Password did not match for user: " + username);
		
		return null;
	}
	
	public void createAccount(String username, String password, UserRole role) throws DuplicateAccountException, IOException {
		
		/**
		 * Don't use the SQLStatementProcessor here since we're actually expecting SQLExceptions when there 
		 * are duplicate nicks
		 */
		PreparedStatement stmt = null;
		Connection con = null;
		try {
			con = connectionPool.getConnection();
			
			stmt = con.prepareStatement("INSERT INTO valid_accounts (username, password_hash, max_registrations) VALUES (?, ?, ?)");
			
			String passHash = "MD5:" + TypeUtil.toHexString(getPasswordHash(password));
			
			stmt.setString(1, username);
			stmt.setString(2, passHash);
			try {	
				stmt.setInt(3, Integer.parseInt(System.getProperty(EmbeddedServer.Setting.KEY_REG_LIMIT_ACCOUNT.getKey())));
			} catch( NumberFormatException e ) { 
				e.printStackTrace();
			}
			
			stmt.executeUpdate();
			stmt.close();
			
			stmt = con.prepareStatement("SELECT uid FROM valid_accounts WHERE username = ?");
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			rs.next();
			long newID = rs.getLong(1);
			stmt.close();
			
			stmt = con.prepareStatement("INSERT INTO user_roles (uid, role_id) VALUES (?, ?)");
			stmt.setLong(1, newID);
			stmt.setLong(2, role.getID());
			stmt.executeUpdate();
			stmt.close();
			
		} catch( SQLException e ) {
			if( e.toString().toLowerCase().contains("duplicate key value") || e.toString().toLowerCase().contains("duplicate entry") ) {
				throw new DuplicateAccountException();
			}
			e.printStackTrace();
			logger.warning(e.toString());
			
			throw new IOException(e.toString());
		}
		finally {
			try {
				con.close();
			} catch( SQLException e ) {
				e.printStackTrace();
				logger.warning(e.toString());
			}
		}
	}

	private byte[] getPasswordHash(String password) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("No MD5!");
		}
		digest.update((password).getBytes());
		byte [] hash_bytes = digest.digest();
		return hash_bytes;
	}

	public int changePassword(final String username, final String pw) {
		
		return (new SQLStatementProcessor<Integer>("UPDATE valid_accounts SET password_hash = ? WHERE username = ?") {
			Integer process( PreparedStatement s ) throws SQLException {
				try {
					String passHash = "MD5:" + TypeUtil.toHexString(getPasswordHash(pw));
					s.setString(1, passHash);
					s.setString(2, username);
					return s.executeUpdate();
				} catch( IOException e ) {
					e.printStackTrace();
					return 0;
				}
			}
		}).doit();
	}

	/**
	 * This method results in the entire DB being reloaded -- call sparingly!
	 */
	public void deleteAccount(final String username) throws IOException {
		
		if( username.equals("admin") ) { 
			throw new IOException("Cannot delete admin");
		}
		
		final StringBuffer doReload = new StringBuffer();
		
		
		/**
		 * For some reason, ON DELETE CASCADE referencing valid_accounts produces a weird derby error, 
		 * so we do this manually. 1) get uid, 2) delete uid's keys, 3) delete uid's account 
		 */
		final Long uid = (new SQLStatementProcessor<Long>("SELECT uid FROM valid_accounts WHERE username = ?") {
			Long process( PreparedStatement s ) throws SQLException {
				s.setString(1, username);
				ResultSet rs = s.executeQuery();
				if( !rs.next() ) {
					return -1L;
				}
				return rs.getLong(1);
			}
		}).doit();
		
		if( uid == -1 ) {
			throw new IOException("no such user");
		}
		
		logger.fine("got uid: " + uid); 
		
		(new SQLStatementProcessor<Void>("DELETE FROM registered_keys WHERE created_by_account = ?") {
			Void process( PreparedStatement s ) throws SQLException {
				s.setLong(1, uid);
				int deleted = 0;
				/**
				 * Best effort consistency -- this delete might take a while, so we might as well delay 
				 * requests that would be inconsistent if possible before the reload 
				 */
				synchronized(CommunityDAO.this) {
					deleted = s.executeUpdate();
				}
				logger.info("Deleted " + deleted + " before deleting account: " + username);
				return null;
			}
		}).doit();
		
		IOException out = (new SQLStatementProcessor<IOException>("DELETE FROM valid_accounts WHERE username = ?") {
			IOException process( PreparedStatement s ) throws SQLException {
				s.setString(1, username);
				logger.finest("Executing delete user update: " + username);
				int updated = s.executeUpdate();
				if( updated == 0 ) {
					return new IOException("No such user? " + updated);
				} else {
					/**
					 * we probably just removed a bunch of keys, re-sync
					 */
					logger.fine("Removed user, setting reload");
					doReload.append("t");
				}
				return null;
			}
		}).doit();
		if( out != null ) {
			throw out;
		}
		
		if( doReload.length() > 0 ) {
			logger.fine("Reloading...");
			load();
		}
	}
	
	public KeyRegistrationRecord getKeyForID( final long id ) { 
		return id_to_record.get(id);
	}

	/**
	 * This method results in the entire DB being reloaded -- call sparingly!
	 * 
	 * This is called from the admin servlet when manually deleting. Pruning of the table at least 
	 * batches the removals and only reloads once. 
	 */
	public synchronized int deregisterKey(final String base64PublicKey) throws NoSuchUserException {
		
		if( peers.remove(new KeyRegistrationRecord(base64PublicKey)) == false ) {
			throw new NoSuchUserException(base64PublicKey);
		}
		
		if( key_to_record.remove(base64PublicKey) == null ) {
			logger.warning("Inconsistent key->record / peers list state: " + base64PublicKey);
		}
		
		int updated = (new SQLStatementProcessor<Integer>("DELETE FROM registered_keys WHERE public_key = ?") {
			Integer process( PreparedStatement s ) throws SQLException {
				s.setString(1, base64PublicKey);
				int updated = s.executeUpdate();
				if( updated == 0 ) {
					logger.warning("Inconsist DB/peer cache state wrt " + base64PublicKey);
				}
				return updated;
			}
		}).doit();
		
		if( updated > 0 ) { 
			load();
			logger.info("Admin removed key: " + base64PublicKey);
		}
		
		return updated;
	}

	public void registerVPN(Set<String> vpn_ids) {
		mVPN_ids = vpn_ids;
		
		logger.info("Registered: " + vpn_ids.size() + " infrastructure keys");
	}

	public List<KeyRegistrationRecord> getPeers(String inBase64Key) {
		long start = System.currentTimeMillis();
		List<KeyRegistrationRecord> out;
		
		int maxFriendsToReturn = 26;
		try { 
			maxFriendsToReturn = Integer.parseInt(System.getProperty(EmbeddedServer.Setting.MAX_FRIENDS_RETURNED.getKey()));
		} catch( Exception e ) { 
			e.printStackTrace();
		}
		
		if( mVPN_ids.size() == 0 ) {
//			out = getNearestPeers(inBase64Key, maxFriendsToReturn, true);
			out = getRandomPeers(inBase64Key, maxFriendsToReturn, (int)Math.round(1.5 * (double)maxFriendsToReturn), true);
		} else {
			out = getVPNList(inBase64Key);
		}
		logger.finer("getPeers() took: " + (System.currentTimeMillis()-start));
		return out;
	}

	public List<CommunityAccount> getAccounts() {
		return (new SQLStatementProcessor<List<CommunityAccount>>("SELECT * FROM valid_accounts") {
			List<CommunityAccount> process( PreparedStatement s ) throws SQLException {
				List<CommunityAccount> out = new ArrayList<CommunityAccount>();
				ResultSet rs = s.executeQuery();
				while( rs.next() ) {
					out.add(accountFromResultSet(con, rs));
				}
				return out;
			}
		}).doit();
	}

	public CommunityAccount getAccountInfo(final long uid) {
		return (new SQLStatementProcessor<CommunityAccount>("SELECT * FROM valid_accounts WHERE uid = ?") {
			CommunityAccount process( PreparedStatement s ) throws SQLException {
				s.setLong(1, uid);
				ResultSet rs = s.executeQuery();
				if( rs.next() ) {
					return accountFromResultSet(con, rs);
				}
				return null;
			}
		}).doit();
	}

	public int setMaxRegistrationsForUID( final int maxRegs, final long uid) {
		return (new SQLStatementProcessor<Integer>("UPDATE valid_accounts SET max_registrations = ? WHERE uid = ?") {
			Integer process( PreparedStatement s ) throws SQLException {
				s.setInt(1, maxRegs);
				s.setLong(2, uid);
				return s.executeUpdate();
			}
		}).doit();
	}
	
	public CommunityAccount getAccountForID( final long uid ) {
		return (new SQLStatementProcessor<CommunityAccount>("SELECT * FROM valid_accounts WHERE uid = ?") {
			CommunityAccount process( PreparedStatement s ) throws SQLException {
				s.setLong(1, uid);
				ResultSet rs = s.executeQuery();
				if( rs.next() ) {
					return accountFromResultSet(con, rs);
				} 
				return null;
			}
		}).doit();
	}
	
	public CommunityAccount getAccountForName( final String name ) {
		return (new SQLStatementProcessor<CommunityAccount>("SELECT * FROM valid_accounts WHERE username = ?") {
			CommunityAccount process( PreparedStatement s ) throws SQLException {
				s.setString(1, name);
				ResultSet rs = s.executeQuery();
				if( rs.next() ) {
					return accountFromResultSet(con, rs);
				} 
				return null;
			}
		}).doit();
	}
	
	public boolean hasInfrastructurePeers() { 
		return mVPN_ids.size() > 0;
	}

	public int getLazyUpdateQueueSize() {
		return mLazyDBQueue.size();
	}
	
	public void setURL(String ourUrl) {
		mURL = ourUrl;
	}
	public String getURL() { 
		return mURL;
	}
	
	public void update_preview( final long id, final byte [] previewpng ) {
		(new SQLStatementProcessor<Void>("UPDATE swarm_extras SET previewpng = ? WHERE swarmid = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setLong(2, id);
				s.setBytes(1, previewpng);
				s.executeUpdate();
				return null;
			}}).doit();
	}

	public synchronized void publish_swarm(byte[] torrentbin, byte[] previewpng, final String description, 
			final String category, final CommunityAccount submitter, 
			final String fromIp ) throws DuplicateSwarmRegistrationException, IOException {
		try {
			if( torrentbin == null ) { 
				throw new IOException("Swarm metadata was null."); 
			} 
			
			Map metainfo = BDecoder.decode(torrentbin);
			
			Map info = (Map) metainfo.get("info");
			SHA1Hasher s = new SHA1Hasher();
			final byte [] torrent_hash_bytes = s.calculateHash(BEncoder.encode(info));
			final String torrent_hash_str = ByteFormatter.encodeString(torrent_hash_bytes);
			
			boolean duplicate = (new SQLStatementProcessor<Boolean>("SELECT infohash FROM published_swarms WHERE infohash = ?") {
					Boolean process( PreparedStatement s ) throws SQLException {
						s.setString(1, torrent_hash_str);
						ResultSet rs = s.executeQuery();
						if( rs.next() ) { 
							return true;
						}
						return false;
					}
				}).doit();
			
			if( duplicate ) { 
				throw new DuplicateSwarmRegistrationException(torrent_hash_str);
			}
			
			final String torrent_name = new String((byte[])info.get("name"), "UTF-8");
			List flist = (List) info.get("files");
			final int num_files = flist == null ? 1 : flist.size();
			Long length = (Long)info.get("length");
			
			if( length == null ) { 
				long acc = 0;
				for( int i=0; i<flist.size(); i++ ) { 
					Map file_map = (Map)flist.get(i);
					acc += (Long)file_map.get("length");
				}
				length = acc;
			}
			
			if( System.getProperty(EmbeddedServer.Setting.STORE_TORRENTS.getKey()).equals(
					Boolean.FALSE.toString()) ) {
				torrentbin = null;
				logger.finer("Discarding torrent info due to server setting... (" + torrent_name + ")");
			}
			
			if( System.getProperty(EmbeddedServer.Setting.DISCARD_PREVIEWS.getKey()).equals(
					Boolean.TRUE.toString()) ) {
				previewpng = null;
				logger.finer("Discarding preview due to server setting... (for torrent: " + torrent_name + ")");
			}
			
			final Long length_shadow = length;
			final byte [] torrentbin_shadow = torrentbin;
			final byte [] previewpng_shadow = previewpng;
			boolean success = (new SQLStatementProcessor<Boolean>(
					"INSERT INTO published_swarms (name, num_files, total_size, " +
					"uploaded_by, infohash, bin, ip, category, has_torrent, needs_moderated) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") {
				Boolean process( PreparedStatement s ) throws SQLException {
					try {
						s.setString(1, torrent_name);
						s.setInt(2, num_files);
						s.setLong(3, length_shadow);
						s.setLong(4, submitter == null ? 1 : submitter.getID()); // if no principal is given, guest uploads are attributed to the admin
						s.setString(5, torrent_hash_str);
						s.setBytes(6, torrentbin_shadow);
						s.setString(7, fromIp);
						s.setString(8, category);
						s.setBoolean(9, torrentbin_shadow != null);
						if( submitter != null ) {
							s.setBoolean(10, !submitter.canModerate()); // moderator submissions don't need to be reviewed.
						} else { 
							s.setBoolean(10, true);
						}
						
						s.executeUpdate();
						
						return true;
					} catch( SQLException e ) { 
						logger.warning(e.toString());
						return false;
					}
				}
			}).doit();
			
			if( success != true ) { 
				throw new IOException("Insert failed (see previous errors)");
			}
			
			/**
			 * Get the id of the newly inserted swarm in preparation for inserting the extras
			 */
			final long swarmid = (new SQLStatementProcessor<Long>("SELECT * FROM published_swarms WHERE infohash = ?") {
				Long process( PreparedStatement s ) throws SQLException {
					s.setString(1, torrent_hash_str);
					ResultSet rs = s.executeQuery();
					rs.next();
					return rs.getLong(1);
				}
			}).doit();
			
			/**
			 * Now insert the extras. 
			 */
			(new SQLStatementProcessor<Void>("INSERT INTO swarm_extras (swarmid, previewpng, description) VALUES " +
					"(?, ?, ?)") {
				Void process( PreparedStatement s ) throws SQLException {
					s.setLong(1, swarmid);
					s.setBytes(2, previewpng_shadow);
					s.setString(3, description);
					
					s.executeUpdate();
					
					return null;
				}
			}).doit();
			
		} catch( IOException e ) { 
			throw e;
		} catch( Exception e ) { 
			e.printStackTrace();
			throw new IOException(e.toString());
		}
	}
	
	public PublishedSwarm getSwarm( final long swarmid ) { 
		return (new SQLStatementProcessor<PublishedSwarm>("SELECT * FROM published_swarms WHERE swarmid = ?" ){
			PublishedSwarm process(PreparedStatement s) throws SQLException {
				s.setLong(1, swarmid);
				ResultSet rs = s.executeQuery();
				if( rs.next() ) { 
					return new PublishedSwarm( 
							rs.getLong("swarmid"), 
							rs.getString("name"), 
							rs.getInt("num_files"), 
							rs.getLong("total_size"), 
							rs.getTimestamp("date_uploaded").getTime(), 
							rs.getString("category"), 
							rs.getString("infohash"), 
							rs.getBoolean("removed"), 
							rs.getLong("uploaded_by"), 
							rs.getBoolean("needs_moderated"), 
							rs.getBoolean("has_torrent"));
				}
				return null;
			}}).doit();
	}
	
	public List<PublishedSwarm> selectSwarms( final String nameMatch, final String categoryMatch, final Long userMatch,  
			final int offset, final int limit, final String sortBy, final boolean desc, final boolean isModerator ) {
		
		if( nameMatch != null ) { 
			if( nameMatch.length() <= 2 ) { 
				return new ArrayList<PublishedSwarm>();
			}
		}
		
		StringBuilder sb = new StringBuilder();
		final List<Object> params = new ArrayList<Object>();
		sb.append("SELECT swarmid, name, num_files, total_size, category, date_uploaded, category, " +
				"infohash, removed, uploaded_by, needs_moderated, has_torrent FROM published_swarms");
		boolean appendedWhere = false;
		if( nameMatch != null ) {
			appendedWhere = true;
			sb.append(" WHERE lower(name) LIKE lower(?)" );
			params.add("%"+nameMatch+"%");
		}
		if( categoryMatch != null ) {
			if( !appendedWhere ) { 
				appendedWhere = true;
				sb.append( " WHERE");
			} else { 
				sb.append( " AND");
			}
			sb.append(" category = ?" );
			params.add(categoryMatch);
		}
		if( isModerator == false && 
				System.getProperty(EmbeddedServer.Setting.REQUIRE_SWARM_MODERATION.getKey()).equals(Boolean.TRUE.toString()) ) {
			if( !appendedWhere ) {
				appendedWhere = true;
				sb.append( " WHERE");
			} else { 
				sb.append( " AND");
			}
			sb.append( " needs_moderated = FALSE");
		}
		
		if( isModerator && 
			userMatch != null ) { 
			if( !appendedWhere ) {
				appendedWhere = true;
				sb.append( " WHERE");
			} else { 
				sb.append( " AND");
			}
			sb.append( " uploaded_by = ?");
			params.add(userMatch);
		}
		
		/**
		 * We can't use a prepared statement for sort ordering, so we need to 
		 * restrict the field value manually. 
		 */
		if( sortBy != null ) {
			if( sortBy.equals("name") ||
				sortBy.equals("date_uploaded") ||
				sortBy.equals("total_size") || 
				sortBy.equals("category") ) {
				
				sb.append(" ORDER BY " + sortBy);
				
				if( desc ) { 
					sb.append(" DESC");
				} else { 
					sb.append(" ASC");
				}
			} else { 
				logger.warning("Malformed sort request: " + sortBy);
			}
		}
		
		if( limit > 0 ) { 
			sb.append( " LIMIT ?" );
			params.add(Integer.valueOf(limit));
		}
		
		if( offset > 0 ) { 
			sb.append( " OFFSET ?" );
			params.add(Integer.valueOf(offset));
		}
		
		logger.finest("Query string: " + sb.toString());
		
		return (new SQLStatementProcessor<List<PublishedSwarm>>(sb.toString()){
			List<PublishedSwarm> process(PreparedStatement s) throws SQLException {
				List<PublishedSwarm> out = new ArrayList<PublishedSwarm>();
				for( int i=1; i<=params.size(); i++ ) { 
					Object param = params.get(i-1);
					if( param instanceof Integer ) { 
						s.setInt(i, (Integer)params.get(i-1));
					} else if( param instanceof Long ) { 
						s.setLong(i, (Long)param);
					} else if( param instanceof String ) { 
						s.setString(i, (String)param);
					} else { 
						System.err.println("Parameter of unknown type: " + param.getClass().getName());
					}
				}
				logger.finest(s.toString());
				ResultSet rs = s.executeQuery();
				while( rs.next() ) { 
					PublishedSwarm p = new PublishedSwarm( 
							rs.getLong("swarmid"), 
							rs.getString("name"), 
							rs.getInt("num_files"), 
							rs.getLong("total_size"), 
							rs.getTimestamp("date_uploaded").getTime(), 
							rs.getString("category"), 
							rs.getString("infohash"), 
							rs.getBoolean("removed"), 
							rs.getLong("uploaded_by"), 
							rs.getBoolean("needs_moderated"), 
							rs.getBoolean("has_torrent"));
					out.add(p);
				}
				return out;
			}}).doit();
	}
	
	public PublishedSwarmDetails getSwarmDetails( final long swarmid ) { 
		return (new SQLStatementProcessor<PublishedSwarmDetails>( "SELECT * FROM swarm_extras WHERE swarmid = ?" ){
			PublishedSwarmDetails process(PreparedStatement s) throws SQLException {
				s.setLong(1, swarmid);
				ResultSet rs = s.executeQuery();
				if( rs.next() ) { 
					
					Blob blob = rs.getBlob("previewpng");
					
					return new PublishedSwarmDetails( 
							rs.getLong("swarmid"), 
							rs.getString("description"), 
							rs.getInt("downloads"),
							rs.getString("language"),  
							rs.getInt("upvotes"), 
							rs.getInt("downvotes"), 
							blob != null ? blob.getBytes(1, (int)blob.length()) : null);
					
				} else { 
					return null;
				}
			}}).doit();
	}
	
	public Comment getComment( final long commentID ) { 
		return (new SQLStatementProcessor<Comment>( "SELECT * FROM comments WHERE commentid = ?" ){
			Comment process(PreparedStatement s) throws SQLException {
				s.setLong(1, commentID);
				
				ResultSet rs = s.executeQuery();
				if( rs.next() ) { 
					return new Comment(
							rs.getLong("swarmid"), 
							rs.getLong("commentid"), 
							rs.getString("accountname"), 
							rs.getTimestamp("time").getTime(), 
							rs.getLong("reply_to"), 
							rs.getInt("upvote"), 
							rs.getInt("downvote"), 
							rs.getString("ip"), 
							rs.getString("body"));
				}
				return null;
			}}).doit();
	}
	
	public List<Comment> selectComments( final long swarmID, final int offset, final int limit ) {
		StringBuilder q = new StringBuilder();
		
		q.append("SELECT * FROM comments WHERE swarmid = ? AND removed = FALSE LIMIT ? OFFSET ? ");
		
		return (new SQLStatementProcessor<List<Comment>>(
				q.toString() ){
			List<Comment> process(PreparedStatement s) throws SQLException {
				s.setLong(1, swarmID);
				s.setInt(2, limit);
				s.setInt(3, offset);
				
				ResultSet rs = s.executeQuery();
				List<Comment> out = new ArrayList<Comment>();
				while( rs.next() ) { 
					out.add(new Comment(
							rs.getLong("swarmid"), 
							rs.getLong("commentid"), 
							rs.getString("accountname"), 
							rs.getTimestamp("time").getTime(), 
							rs.getLong("reply_to"), 
							rs.getInt("upvote"), 
							rs.getInt("downvote"), 
							rs.getString("ip"), 
							rs.getString("body")));
				}
				return out;
			}}).doit();
	}
	
	public void postComment( final String username, final long swarmID, final String comment, final long replyTo, final String ip ) throws IOException {
		
		if( comment.length() == 0 ) { 
			return;
		}
		
		if( comment.length() > 4*1024 ) { 
			throw new IOException("Too long");
		}
		
		Boolean good = (new SQLStatementProcessor<Boolean>(
				"INSERT INTO comments (swarmid, accountname, reply_to, ip, body) VALUES (?, ?, ?, ?, ?)" ){
			Boolean process(PreparedStatement s) throws SQLException {
				s.setLong(1, swarmID);
				s.setString(2, username);
				s.setLong(3, replyTo);
				s.setString(4, ip);
				s.setString(5, comment);
				
				s.executeUpdate();
				
				return true;
			}}).doit();
		if( good == null ) { 
			throw new IOException("SQL error");
		}
	}

	public byte[] getSwarmBytes(final long id) {
		return (new SQLStatementProcessor<byte[]>(
				"SELECT bin FROM published_swarms WHERE swarmid = ?") {
			byte[] process(PreparedStatement s) throws SQLException {
				s.setLong(1, id);
				ResultSet rs = s.executeQuery();
				if( rs.next() ) { 
					return rs.getBytes(1);
				}
				return null;
			}}).doit();
	}

	public List<String> getCategories() {
		return (new SQLStatementProcessor<List<String>>("SELECT * FROM categories") {
			public List<String> process( PreparedStatement s ) throws SQLException {
				List<String> out = new ArrayList<String>();
				ResultSet rs = s.executeQuery();
				while( rs.next() ) { 
					out.add(rs.getString(1));
				}
				return out;
			}}).doit();
	}
	
	public void removeComment( final long id ) { 
		(new SQLStatementProcessor<Void>("UPDATE comments SET removed = TRUE WHERE commentid = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setLong(1, id);
				s.executeUpdate();
				return null;
			}}).doit();
	}
	
	public void markSwarmRemoved( final long id, final boolean isRemoved ) { 
		(new SQLStatementProcessor<Void>("UPDATE published_swarms SET removed = ? WHERE swarmid = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setBoolean(1, isRemoved);
				s.setLong(2, id);
				s.executeUpdate();
				return null;
			}}).doit();
	}
	
	public void deleteSwarm( final long id ) { 
		(new SQLStatementProcessor<Void>("DELETE FROM published_swarms WHERE swarmid = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setLong(1, id);
				s.executeUpdate();
				return null;
			}}).doit();
	}
	
	public void setSwarmCategory( final long id, final String category ) throws IOException { 
		(new SQLStatementProcessor<Void>("UPDATE published_swarms SET category = ? WHERE swarmid = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setString(1, category);
				s.setLong(2, id);
				s.executeUpdate();
				return null;
			}}).doit();
	}
	
	public void updateRole( final long id, final String roleStr ) throws IOException {
		if( id == 1 ) { 
			logger.warning("Can't update roles for admin");
			return;
		}
		
		final UserRole neuRole = UserRole.roleForString(roleStr);
		if( neuRole == null ) { 
			logger.warning("Unknown role.");
			return;
		}
		
		(new SQLStatementProcessor<Void>("UPDATE user_roles SET role_id = ? WHERE uid = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setLong(1, neuRole.getID());
				s.setLong(2, id);
				s.executeUpdate();
				return null;
			}}).doit();
		
		/**
		 * Only once we've updated the DB!
		 */
		getAccountForID(id).setRoles(new String[]{neuRole.getTag()});
	}
	
	public int getApproximateRowCount( final String table ) { 
		return (new SQLStatementProcessor<Integer>("SHOW TABLE STATUS LIKE ?") {
			public Integer process( PreparedStatement s ) throws SQLException {
				s.setString(1, table);
				ResultSet rs = s.executeQuery();
				if( rs.next() ) {
					return rs.getInt("rows");
				}
				return -1;
			}}).doit();
	}
	
	public void setSwarmModerated( final long id, final boolean isModerated ) { 
		(new SQLStatementProcessor<Void>("UPDATE published_swarms SET needs_moderated = ? WHERE swarmid = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setBoolean(1, !isModerated);
				s.setLong(2, id);
				s.executeUpdate();
				return null;
			}}).doit();
	}
	
	public void deleteCategory( final String category ) { 
		(new SQLStatementProcessor<Void>("DELETE FROM categories WHERE category = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setString(1, category);
				s.executeUpdate();
				return null;
			}}).doit();
	}
	
	public void updateDescription( final long id, final String description ) { 
		(new SQLStatementProcessor<Void>("UPDATE swarm_extras SET description = ? WHERE swarmid = ?") {
			public Void process( PreparedStatement s ) throws SQLException {
				s.setString(1, description);
				s.setLong(2, id);
				s.executeUpdate();
				return null;
			}}).doit();
	}
	
	public boolean hasPermissions( CommunityAccount who, PublishedSwarm swarm ) {
		if( swarm == null ) { 
			return false;
		}
		
		boolean canModerate = false;
		if( who != null ) { 
			canModerate = who.canModerate();
		}
		
		if( canModerate ) { 
			return true;
		}
		
		
		if( swarm.isNeeds_moderated() && System.getProperty(EmbeddedServer.Setting.REQUIRE_SWARM_MODERATION.getKey()).equals(Boolean.TRUE.toString()) ) {
			return false;
		} 
		
		if( swarm.isRemoved() ) {
			return false;
		}
		
		return true;
	}
	
	public long getNextUnmoderatedID() { 
		return (new SQLStatementProcessor<Long>("SELECT swarmid FROM published_swarms WHERE needs_moderated = TRUE LIMIT 1") {
			public Long process( PreparedStatement s ) throws SQLException {
				ResultSet rs = s.executeQuery();
				if( rs.next() ) { 
					return rs.getLong(1);
				}
				return -1L;
			}}).doit();
	}
}
