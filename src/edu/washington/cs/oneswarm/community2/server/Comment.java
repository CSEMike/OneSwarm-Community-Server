package edu.washington.cs.oneswarm.community2.server;

public class Comment {
	long swarmID;
	long commentID;
	
	String accountName;
	
	long timestamp;
	
	long replyTo;
	
	int upvote, downvote;
	
	String ip;
	
	String comment;
	
	public Comment( long swarmID, long commentID, String accountName, long timestamp, 
			long replyTo, int upvote, int downvote, String ip, String comment ) { 
		this.swarmID = swarmID;
		this.commentID = commentID;
		this.accountName = accountName;
		this.timestamp = timestamp;
		this.replyTo = replyTo;
		this.upvote = upvote;
		this.downvote = downvote;
		this.ip = ip;
		this.comment = comment;
	}

	public long getSwarmID() {
		return swarmID;
	}

	public long getCommentID() {
		return commentID;
	}

	public String getAccountName() {
		return accountName;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public long getReplyTo() {
		return replyTo;
	}

	public int getUpvote() {
		return upvote;
	}

	public int getDownvote() {
		return downvote;
	}

	public String getIp() {
		return ip;
	}

	public String getComment() {
		return comment;
	}
}
