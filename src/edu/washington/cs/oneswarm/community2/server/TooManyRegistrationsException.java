package edu.washington.cs.oneswarm.community2.server;

import java.io.IOException;

public class TooManyRegistrationsException extends IOException {
	private int howmany;

	public TooManyRegistrationsException( int howmany ) {
		super("Too many registrations: " + howmany);
		this.howmany = howmany;
	}
	
	public TooManyRegistrationsException() {
		super("Too many registrations");
	}
}
