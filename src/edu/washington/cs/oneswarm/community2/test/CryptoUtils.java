package edu.washington.cs.oneswarm.community2.test;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

public class CryptoUtils {
	private KeyPairGenerator generator;
	private Signature signer = null;
	private MessageDigest digest = null;
	public static final int KEY_SIZE_BITS = 1024;
	
	public CryptoUtils() {
		try {
			generator = KeyPairGenerator.getInstance("RSA");
			signer = Signature.getInstance("SHA1withRSA");
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		} 
		generator.initialize(KEY_SIZE_BITS);
	}
	
	public KeyPair getPair() {
		return generator.generateKeyPair();
	}
	
	public static String getBase64FromKey( Key inKey ) {
		return Base64.encode(inKey.getEncoded()).replaceAll("\n","");
	}
}
