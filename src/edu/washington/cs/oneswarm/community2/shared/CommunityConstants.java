package edu.washington.cs.oneswarm.community2.shared;

public final class CommunityConstants {
	public static final String VERSION = "0.7pre";
	
	/**
	 * Cookies
	 */
	public static final String ADMIN_SESSION_COOKIE = "community_session";
	
	/**
	 * Form field names
	 */
	public static final String BASE64_PUBLIC_KEY = "base64key";
	public static final String NICKNAME = "nick";
	public static final String CHALLENGE_RESPONSE = "resp";
	
	/**
	 * Form response bodies
	 */
	public static final String REGISTRATION_SUCCESS = "REGISTRATION_OK";
	public static final String REGISTRATION_DUPLICATE = "REGISTRATION_DUPLICATE";
	public static final String REGISTRATION_RATE_LIMITED = "REGISTRATION_RATE_LIMITED";
	
	public static final String CHALLENGE = "CHALLENGE";
	
	/**
	 * XML elements and attributes
	 */
	public static final String RESPONSE_ROOT = "CommunityServerResponse";
	public static final String REFRESH_INTERVAL = "RefreshInterval";
	public static final String FRIEND_LIST = "FriendList";
	public static final String FRIEND = "Friend";
	public static final String KEY_ATTRIB = "Base64Key";
	public static final String NICK_ATTRIB = "nick";

	public static final int MAX_NICK_LENGTH = 128;
}
