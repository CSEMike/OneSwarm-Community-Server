package edu.washington.cs.oneswarm.community2.utils;

import java.io.IOException;

import java.io.PrintStream;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class IPServletFilter implements Filter {
	private List<IPFilter> whitelist;
	private List<IPFilter> blacklist;
	
	private static Logger logger = Logger.getLogger(IPServletFilter.class.getName());
	
	public IPServletFilter( List<IPFilter> whitelist, List<IPFilter> blacklist) {
		this.whitelist = whitelist;
		this.blacklist = blacklist;
	}
	
	public void destroy() {}

	public void doFilter(ServletRequest request, 
			ServletResponse response, 
			FilterChain chain) throws IOException, ServletException {
		
		for( IPFilter filter : blacklist ) { 
			if( filter.contains(request.getRemoteAddr()) ) {
				logger.warning("Dropped blacklisted connection request: " + request.getRemoteAddr());
				if( response instanceof HttpServletResponse ) {
					HttpServletResponse resp = ((HttpServletResponse)response);
					sendNoAuth(resp);
				}
				return;
			} 
		}
		// check whitelist if it has any entries (and we're not connecting from localhost)
		if( whitelist.size() > 0 && request.getRemoteAddr().equals("127.0.0.1") == false ) {
			boolean ok = false;
			for( IPFilter filter : whitelist ) { 
				if( filter.contains(request.getRemoteAddr()) ) {
					ok = true;
					break;
				}
			}
			
			if( !ok ) {
				logger.warning("Dropped connection request from user not in whitelist: " + request.getRemoteAddr());
				if( response instanceof HttpServletResponse ) {
					HttpServletResponse resp = ((HttpServletResponse)response);
					sendNoAuth(resp);
				}
				return;
			}
		}
		/**
		 * Pass-through, this request is okay. 
		 */
		if( chain != null ) {
			chain.doFilter(request, response);
		}
	}

	private void sendNoAuth(HttpServletResponse resp) throws IOException {
		resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		PrintStream out = new PrintStream(resp.getOutputStream());
		out.println("<html><body><h1>401/IP not authorized</h1></body></html>\r\n\r\n");
		out.flush();
	}

	public void init(FilterConfig config) throws ServletException {}
}