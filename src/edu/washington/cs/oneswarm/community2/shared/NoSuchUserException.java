package edu.washington.cs.oneswarm.community2.shared;

public class NoSuchUserException extends Exception {

	private String key;

	public NoSuchUserException(String base64PublicKey) {
		super("No such user: " + base64PublicKey);
		key = base64PublicKey;
	}
	
	public NoSuchUserException() {}
}
