package edu.washington.cs.oneswarm.community2.server;

import java.security.Principal;

import edu.washington.cs.oneswarm.community2.server.CommunityDAO.UserRole;

public class CommunityAccount implements Principal {
	
	private String username;
	private String pw_hash;
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
	
	public boolean canModerate() { 
		for( String s : roles ) { 
			if( s.equals(UserRole.ADMIN.getTag()) ) { 
				return true;
			}
			if( s.equals(UserRole.MODERATOR.getTag()) ) { 
				return true;
			}
		}
		return false;
	}
	
	public boolean isAdmin() { 
		for( String s : roles ) { 
			if( s.equals(UserRole.ADMIN.getTag()) ) { 
				return true;
			}
		}
		return false;
	}

	private int max_registrations;
	private String[] roles;

	public CommunityAccount( String username, String pw_hash, String [] roles, int registrations, int max_registrations, long uid ) {
		this.username = username;
		this.pw_hash = pw_hash;
		this.roles = roles;
		this.registrations = registrations;
		this.max_registrations = max_registrations;
		this.uid = uid;
	}
	
	public CommunityAccount() {} // for serialization support
	
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
	
	public void setRoles( String [] roles) { 
		this.roles = roles;
	}
	
	public int hashCode() { 
		return username.hashCode();
	}
	
	public boolean equals( Object rhs ) {
		if( rhs instanceof CommunityAccount ) {
			return ((CommunityAccount)rhs).username.equals(username);
		}
		return false;
	}
	
	public String toString() { 
		StringBuilder sb = new StringBuilder();
		for( String r : getRoles() ) {
			sb.append(r + " ");
		}
		return "Name: " + getName() + " Roles: " + sb.toString() + " ID: " + getID();
	}
}
