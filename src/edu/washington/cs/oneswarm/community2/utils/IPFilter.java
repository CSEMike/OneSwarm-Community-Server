package edu.washington.cs.oneswarm.community2.utils;

import java.net.UnknownHostException;
import java.util.BitSet;

public class IPFilter {
	private BitSet prefix;
	private int sigBits;
	
	private int lower = Integer.MIN_VALUE, upper = Integer.MAX_VALUE; // if we're using an IP address range

	public IPFilter( String prefix ) throws UnknownHostException {
		
		if( prefix.contains("-") ) {
			this.prefix = null;
			String [] toks = prefix.split("-");
			lower = ByteManip.aton(toks[0]);
			upper = ByteManip.aton(toks[1]);
		}
		else {
			String [] toks = prefix.split("/");
			sigBits = Integer.parseInt(toks[1]);
			this.prefix = fromByteArray(ByteManip.intToByteArray(ByteManip.aton(toks[0])));
		}
	}
	
	public IPFilter( String prefix, int sigBits ) throws UnknownHostException {
		this(ByteManip.ip_to_l(prefix), sigBits);
	}
	
	public IPFilter( int prefix, int sigBits ) {
		this.prefix = fromByteArray(ByteManip.intToByteArray(prefix));
		this.sigBits = sigBits;
	}
	
	public boolean contains( String ip ) throws UnknownHostException {
		return contains(ByteManip.aton(ip));
	}
	
	public boolean contains( int ip ) { 
		if( prefix != null ) {
			BitSet bits = fromByteArray(ByteManip.ltob(ip));
			for( int i=31; i>=(32-sigBits); i-- ) { 
				if( bits.get(i) != prefix.get(i) ) {
					return false;
				}
			}
			return true;
		} else {
			return ip < upper && ip > lower;
		}
	}
	
	private static BitSet fromByteArray(byte[] bytes) {
        BitSet bits = new BitSet();
        for (int i=0; i<bytes.length*8; i++) {
            if ((bytes[bytes.length-i/8-1]&(1<<(i%8))) > 0) {
                bits.set(i);
            }
        }
        return bits;
    }
	
	public String toString() { 
		StringBuilder sb = new StringBuilder();
		for( int i=0; i<32; i++ ) { 
			sb.append(this.prefix.get(i) == true ? "1" : "0");
		}
		return sb.toString();
	}
	
	public static final void main( String [] args ) throws Exception { 
		IPFilter prefix = new IPFilter("128.208.5.5/24");
		System.out.println("prefix: " + prefix);
		System.out.println(prefix.contains("128.208.4.255"));
		
		IPFilter filt = new IPFilter("128.208.5.0-128.208.7.10");
		System.out.println(filt.contains("128.208.4.0"));
		System.out.println(filt.contains("128.208.5.1"));
		System.out.println(filt.contains("128.208.6.24"));
		System.out.println(filt.contains("128.208.7.11"));
		System.out.println(filt.contains("129.208.7.11"));
		
	}
}
