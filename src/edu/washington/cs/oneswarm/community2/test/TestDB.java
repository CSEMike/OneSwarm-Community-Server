package edu.washington.cs.oneswarm.community2.test;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.LogManager;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.community2.server.CommunityDAO;
import edu.washington.cs.oneswarm.community2.shared.KeyRegistrationRecord;

public class TestDB {
	
	private CommunityDAO dao;
	
	public void run() throws Exception {
		dao = CommunityDAO.get();
		
		dao.drop_tables();
		dao.create_tables();
		
		dao.registerUser("abc123", "Žstwtf?", "1.2.3.4", "admin");
		
////		
//		Runnable r = new Runnable() {
//			public void run() {
//				try {
//					random_inserts(dao, 2000);
//				} catch( Exception e ) {
//					e.printStackTrace();
//				}
//			}
//		};
//		(new Thread(r)).start();
//		(new Thread(r)).start();
		
//		FriendRecord [] peers = dao.getRegisteredKeys();
//		Random r = new Random();
		
//		long start = System.currentTimeMillis();
//		CryptoUtils c = new CryptoUtils();
//		CommunityDAO db = CommunityDAO.get();
//		
//		List<String> keys = new LinkedList<String>();
//		for( int q=0; q<100; q++ ) {
//			if( (q%100) == 0 ) {
//				System.out.println(q + " keys");
//			}
//			
//			keys.add( Base64.encode(c.getPair().getPublic().getEncoded()).replaceAll("\n","") );
//		}		
//		
//		start = System.currentTimeMillis();
//		Random rand = new Random();
//		while( keys.size() > 0 ) {
//			
//			if( rand.nextDouble() < 0.75 ) {
//				String k = keys.remove(0);
//				String nick = "r-" + k.hashCode();
//				db.registerUser(k, nick, "127.0.0.1", "admin");
//				dao.getPeers(k);
//			} else {
//				FriendRecord [] registered = dao.getRegisteredKeys();
//				if( registered.length > 0 ) {
//					dao.deregisterKey(registered[rand.nextInt(registered.length)].getBase64PublicKey());
//				}
//			}
//			
//		}
//		
//		System.out.println("total db ops: " + (System.currentTimeMillis()-start));
		
//		PrintStream fout = new PrintStream(new FileOutputStream("/tmp/topotest"));
//		CommunityDAO.get().dumpTable("topology", fout, "\n");
		
	}
	
	public static int random_inserts( CommunityDAO db, int howmany ) throws Exception {
	
		CryptoUtils c = new CryptoUtils();
		List<String> keys = new ArrayList<String>();
		long start = System.currentTimeMillis();
		
		for( int i=0; i<howmany; i++ ) {
			keys.add(Base64.encode(c.getPair().getPublic().getEncoded()).replaceAll("\n",""));
			
			if( (i % 10) == 0 ) {
				System.out.println("key generation: " + i + " / " +howmany);
			}
			
		}
		System.out.println("key generation took: " + (System.currentTimeMillis() - start)/1000);
		
		start = System.currentTimeMillis();
		int registered = 0;
		for( int kItr=0; kItr<keys.size(); kItr++ ) {
			String k = keys.get(kItr);
			String nick = "rand-" + k.hashCode();
			db.registerUser(k, nick, "127.0.0.1", "admin");
			registered++;
			
			if( (kItr % 100) == 0 ) {
				System.out.println("inserts: " + kItr + " / " + keys.size());
			}
		}
		System.out.println("db inserts: " + (System.currentTimeMillis()-start)/1000);
		return registered;
	}
	
	public static final void main( String [] args ) throws Exception {
		
		try {
			LogManager.getLogManager().readConfiguration(new FileInputStream("./logging.properties"));
			System.out.println("read log configuration");
		} catch( Exception e ) {
			System.err.println("error reading log config: " + e.toString());
		}
		
		(new TestDB()).run();	
	}

	public static int random_deletes(CommunityDAO dao, int howmany) {
		
		KeyRegistrationRecord [] recs = dao.getRegisteredKeys();
		Collections.shuffle(Arrays.asList(recs));
		int removed = 0;
		for( int i=0; i<howmany && i<recs.length; i++ ) { 
			try {
				dao.deregisterKey(recs[i].getBase64PublicKey());
				removed++;
			}catch( Exception e ) {
				e.printStackTrace();
			}
		}
		return removed;
	}
}
