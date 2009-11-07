package edu.washington.cs.oneswarm.community2.server;

import java.io.IOException;

public class DuplicateSwarmRegistrationException extends IOException {
	public DuplicateSwarmRegistrationException(String torrentHashStr) {
		super(torrentHashStr);
	}

	private static final long serialVersionUID = 1L;
}
