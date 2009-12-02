package edu.washington.cs.oneswarm.community2.shared;


/**
 * Just like CommunityAccount in the server package, except doesn't implement Principal (since GWT can't deal with it)
 */

public class CommunityAccountLite {

	private String username;
	private String pw_hash;
	private String [] roles;
	private int registrations;
	private long uid;
	
	public int getRegistrations() {
		return registrations;
	}

	public void setRegistrations(int registrations) {
		this.registrations = registrations;
	}

	public int getMaxRegistrations() {
		return max_registrations;
	}

	public void setMaxRegistrations(int max_registrations) {
		this.max_registrations = max_registrations;
	}

	private int max_registrations;

	public CommunityAccountLite( String username, String pw_hash, String [] roles, int registrations, int max_registrations, long uid ) {
		this.username = username;
		this.pw_hash = pw_hash;
		this.roles = roles;
		this.registrations = registrations;
		this.max_registrations = max_registrations;
		this.uid = uid;
	}
	
	public CommunityAccountLite() {} // for serialization support
	
	public long getID() { 
		return uid;
	}
	
	public String getName() {
		return username;
	}
	
	public String getHash() { 
		return pw_hash;
	}
	
	public String [] getRoles() { 
		return roles;
	}
	
	public int hashCode() { 
		return username.hashCode();
	}
	
	public boolean equals( Object rhs ) {
		if( rhs instanceof CommunityAccountLite ) {
			return ((CommunityAccountLite)rhs).username.equals(username);
		}
		return false;
	}
	
	public String toString() { 
		return "Name: " + getName() + " Roles: " + getRoles().length;
	}
}
