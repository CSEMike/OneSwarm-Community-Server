package edu.washington.cs.oneswarm.community2.shared;

import java.util.Date;

public class KeyRegistrationRecord implements Comparable<KeyRegistrationRecord> {
	String nickname;
	String base64key;
	String registrationIP;
	long createdByID;
	Date registeredDate;
	Date lastRefreshedDate;
	private Long mID;
	
	/**
	 * This is a convenience constructor that's only used for comparison (e.g., in the binary search of the peer list) when 
	 * we only have a public key available and we need to construct a dummy FriendRecord. 
	 * 
	 * In other circumstances, this should be created from a database request and the resulting ResultSet (or from the full constructor)
	 */
	public KeyRegistrationRecord( String inBase64Key ) {
		base64key = inBase64Key;
	}
	
	public void setLastRefresh( Date inRefreshTime ) { 
		lastRefreshedDate = inRefreshTime;
	}
	
	public KeyRegistrationRecord() {} // for serializability
	
	public KeyRegistrationRecord( String base64Key, String nick, Date registeredDate, Date lastRefreshedDate, String registrationIP, long createdByID, long id ) {
		this.base64key = base64Key;
		this.nickname = nick;
		this.mID = id;
		this.registeredDate = registeredDate;
		this.lastRefreshedDate = lastRefreshedDate;
		this.registrationIP = registrationIP;
		this.createdByID = createdByID;
		
		if( this.nickname == null ) {
			nickname = "Unspecified-" + base64key.hashCode();
		}
		else if( this.nickname.length() == 0 ) {
			nickname = "Unspecified-" + base64key.hashCode();
		}
	}
	
	public String getNickname() {
		return nickname;
	}

	public String getBase64PublicKey() {
		return base64key;
	}
	
	public String getRegistrationIP() {
		return registrationIP;
	}

	public long getCreatedByID() {
		return createdByID;
	}
	
	public Date getRegisteredDate() {
		return registeredDate;
	}

	public Date getLastRefreshedDate() {
		return lastRefreshedDate;
	}
	
	public int compareTo(KeyRegistrationRecord o) {
		int thisVal = this.base64key.hashCode();
		int anotherVal = o.base64key.hashCode();
		return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
	}
	public boolean equals( Object rhs ) {
		if( rhs instanceof KeyRegistrationRecord ) {
			return base64key.equals(((KeyRegistrationRecord)rhs).base64key);
		}
		return false;
	}
	
	public int hashCode() { 
		return base64key.hashCode();
	}
	
	public String toString() { 
		return nickname + " " + base64key.hashCode() + " / " + base64key;
	}

	public Long getID() {
		return mID;
	}
}
