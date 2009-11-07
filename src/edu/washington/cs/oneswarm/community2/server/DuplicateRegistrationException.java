package edu.washington.cs.oneswarm.community2.server;

import java.io.IOException;

public class DuplicateRegistrationException extends IOException {
	public DuplicateRegistrationException(String dup) {
		super("Duplicate registration: " + dup);
	}
}
