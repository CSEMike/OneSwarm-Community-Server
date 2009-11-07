package edu.washington.cs.oneswarm.community2.server;

public class PublishedSwarmDetails {

	long swarmID;
	
	String description;
	
	int downloads;
	String language;
	
	int upvotes, downvotes;
	
	byte [] previewPNG;

	public PublishedSwarmDetails( long swarmID, String description, 
			int downloads, String language, 
			int upvotes, int downvotes, byte [] previewPNG ) { 
		this.swarmID = swarmID;
		this.description = description;
		this.downloads = downloads;
		this.language = language;
		this.upvotes = upvotes;
		this.downloads = downvotes;
		this.previewPNG = previewPNG;
	}
	
	public long getSwarmID() {
		return swarmID;
	}

	public String getDescription() {
		return description;
	}

	public int getDownloads() {
		return downloads;
	}

	public String getLanguage() {
		return language;
	}

	public int getUpvotes() {
		return upvotes;
	}

	public int getDownvotes() {
		return downvotes;
	}

	public byte[] getPreviewPNG() {
		return previewPNG;
	}
	
	
}
