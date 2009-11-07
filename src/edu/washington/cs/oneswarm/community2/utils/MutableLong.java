package edu.washington.cs.oneswarm.community2.utils;

public class MutableLong {
	public long v;
	public MutableLong( long v ) {
		set(v);
	}
	public long get() { 
		return v;
	}
	public void set(long v) {
		this.v = v;
	}
}
