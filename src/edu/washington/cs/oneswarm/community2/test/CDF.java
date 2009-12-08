package edu.washington.cs.oneswarm.community2.test;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CDF {
	private String name;

	public CDF(String name) {
		this.name = name;
	}
	
	List<Comparable> vals = new ArrayList<Comparable>();
	
	public synchronized void addValue( Comparable n ) { 
		vals.add(n);
	}
	
	public void draw() { 
		Collections.sort(vals);
		
		try {
			PrintStream out = new PrintStream(new FileOutputStream("/tmp/cdf-" + name));
			for( int i=0; i<vals.size(); i++ ) {
				out.format("%3.16f %3.16f\n", (double)i / (double)vals.size(), Double.parseDouble(vals.get(i).toString())); 
			}
			out.flush();
			
		} catch( IOException e ) {
			e.printStackTrace();
		}
	}
}
