package edu.washington.cs.oneswarm.community2.server;

import java.security.Principal;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DownloadServlet extends javax.servlet.http.HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static Logger logger = Logger.getLogger(DownloadServlet.class.getName());
	
	public DownloadServlet() {
		CommunityDAO.get();
		logger.info("Started DL servlet");
	}
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		try { 
			CommunityDAO dao = CommunityDAO.get();
			long id = Long.parseLong(request.getParameter("id"));
			PublishedSwarm swarm = CommunityDAO.get().getSwarm(id);
			
			Principal p = request.getUserPrincipal();
			CommunityAccount acct = null;
			if( p != null ) { 
				acct = dao.getAccountForName(p.getName());
			}
			if( dao.hasPermissions(acct, swarm) == false ) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			
			response.setContentType("application/x-oneswarm");
			byte [] b = CommunityDAO.get().getSwarmBytes(id);
			
			if( b == null ) { 
				logger.warning("Problem during swarm download: null swarm bytes");
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
			
			response.setContentLength(b.length);
			response.setHeader("Content-Disposition", "attachment; filename=" + swarm.getName() + ".oneswarm");
			
			response.getOutputStream().write(b);
			
		} catch( Exception e ) {
			logger.warning("Problem during swarm download: " + e.toString());
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			e.printStackTrace();
		}
	}
}
