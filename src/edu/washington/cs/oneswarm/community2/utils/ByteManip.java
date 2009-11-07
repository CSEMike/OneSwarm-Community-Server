package edu.washington.cs.oneswarm.community2.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ByteManip {
	public static long btol(byte[] b) {
		long l = 0;
		for(int i =0; i < 8; i++){      
		   l <<= 8;
		   l ^= (long) b[i] & 0xFF;
		}
		return l;
	}

	public static byte[] ltob(long l) {
		byte[] b = new byte[8];
		for (int i = 0; i < 8; i++) {
			b[7-i] = (byte) (l >>> (i * 8));
		}
		return b;
	}
	
	public static String ntoa( int ip )
	{
		long a = (ip & 0xFF000000) >>> 24;
		long b = (ip & 0x00FF0000) >>> 16;
		long c = (ip & 0x0000FF00) >>> 8;
		long d = (ip & 0x000000FF) >>> 0;
		
		return a + "." + b + "." + c + "." + d;
	}
	
	public static int aton( String ip )
	{
		String [] toks = ip.split("\\.");
		int a = Integer.parseInt(toks[0]);
		int b = Integer.parseInt(toks[1]);
		int c = Integer.parseInt(toks[2]);
		int d = Integer.parseInt(toks[3]);
		
		return (int)((a << 24) | (b << 16) | (c << 8) | d);
	}

	public static int ip_to_l(String ip) throws UnknownHostException {
		byte[]	bytes = InetAddress.getByName(ip).getAddress();
		return (bytes[0]<<24) & 0xff000000 | 
				(bytes[1] << 16) & 0x00ff0000 | 
				(bytes[2] << 8) & 0x0000ff00 | 
				bytes[3] & 0x000000ff;
	}
	
   public static byte[] intToByteArray(int value) {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((value >>> offset) & 0xFF);
        }
        return b;
    }
}
