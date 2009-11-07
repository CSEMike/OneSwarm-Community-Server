package edu.washington.cs.oneswarm.community2.server;

public class PublishedSwarm {
	long swarmID;
	String name;
	int fileCount;
	long totalSize;
	long uploadedTimestamp;
	String category;
	String infohash;
	private boolean removed;
	long uploadedBy;
	boolean needs_moderated;
	boolean hasTorrent;
	
	public PublishedSwarm( long swarmID, String name, int fileCount, long totalSize, 
			long uploadedTimestamp, String category, String infohash, boolean removed, 
			long uploadedBy, boolean needs_moderated, boolean hasTorrent ) { 
		this.swarmID = swarmID;
		this.name = name;
		this.fileCount = fileCount;
		this.totalSize = totalSize;
		this.category = category;
		this.uploadedTimestamp = uploadedTimestamp;
		this.infohash = infohash;
		this.removed = removed;
		this.uploadedBy = uploadedBy;
		this.needs_moderated = needs_moderated;
		this.hasTorrent = hasTorrent;
	}
	
	public boolean isHasTorrent() { 
		return hasTorrent;
	}
	
	public boolean isNeeds_moderated() { 
		return needs_moderated;
	}
	
	public long getUploadedBy() { 
		return uploadedBy;
	}
	
	public boolean isRemoved() {
		return removed;
	}
	public long getSwarmID() {
		return swarmID;
	}
	public String getName() {
		return name;
	}
	public int getFileCount() {
		return fileCount;
	}
	public long getTotalSize() {
		return totalSize;
	}
	public long getUploadedTimestamp() {
		return uploadedTimestamp;
	}
	public String getCategory() { 
		return category;
	}
	public String getInfohash() { 
		return infohash;
	}
}
