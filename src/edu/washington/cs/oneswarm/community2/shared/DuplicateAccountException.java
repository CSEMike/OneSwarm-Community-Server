package edu.washington.cs.oneswarm.community2.shared;

public class DuplicateAccountException extends Exception {
	public String toString() { 
		return "Duplicate account name";
	}
}
