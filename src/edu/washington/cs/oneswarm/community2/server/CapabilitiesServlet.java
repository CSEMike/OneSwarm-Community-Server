package edu.washington.cs.oneswarm.community2.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.washington.cs.oneswarm.community2.server.EmbeddedServer.StartupSetting;

public class CapabilitiesServlet extends javax.servlet.http.HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static Logger logger = Logger.getLogger(CapabilitiesServlet.class.getName());
	
	String [] filterKeywords = null;
	
	public CapabilitiesServlet() {
		logger.info("Capabilities servlet started.");
		
		String filterFileName = System.getProperty(EmbeddedServer.StartupSetting.SEARCH_FILTER_FILE.getKey());
		if( filterFileName == null ) {
			filterKeywords = null;
		} else {
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(filterFileName);
				BufferedReader in = new BufferedReader(new InputStreamReader(fis));
				List<String> scratch = new ArrayList<String>();
				
				while( true ) { 
					String line = in.readLine();
					if( line == null ) {
						break;
					}
					line = line.trim();
					
					String [] toks = line.split("\\s+");
					for( String s : toks ) {
						scratch.add(s);
						logger.fine("Filter keyword: " + s);
					}
				}
				filterKeywords = scratch.toArray(new String[0]);
				
			} catch( IOException e ) {
				logger.warning("Error reading filter keywords: " + e.toString());
				e.printStackTrace();
			} finally {
				if( fis != null ) {
					try {
						fis.close();
					} catch( IOException e ) {}
				}
			}
		}
	}
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		try { 
			PrintStream out = new PrintStream(response.getOutputStream());
			
			out.println("<capabilities>");
			out.println("<peers path=\"community/\"/>");
			// always give publish -- if clients don't have perms, they will see an error (and can fix) vs. the pain 
			// of not having publish
//			if( request.getUserPrincipal() != null ) {
				out.println("<publish path=\"publish/\"/>");
//			}
			out.println("<splash path=\"files.jsp\"/>");
			out.println("<id name=\"" + System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) + "\"/>");
			if( System.getProperty(EmbeddedServer.Setting.RSS_BASE_URL.getKey()) != null ) {
				out.println("<rss path=\"/rss\"/>");
			}
			
			if( System.getProperty(EmbeddedServer.StartupSetting.UNENCRYPTED_PORT.getKey()) != null ) {
				int alt_port = Integer.parseInt(System.getProperty(StartupSetting.UNENCRYPTED_PORT.getKey()));
				out.println("<nossl port=\"" + alt_port + "\"/>");
			}
			
			if( filterKeywords != null ) {
				out.println("<searchfilter>");
				for( String keyword : filterKeywords ) {
					out.println("<keyword>" + keyword + "</keyword>");
				}
				out.println("</searchfilter>");
			}
			
			out.println("</capabilities>");
			out.flush();
			out.close();
			
		} catch( IOException e ) { 
			logger.warning(e.toString());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
