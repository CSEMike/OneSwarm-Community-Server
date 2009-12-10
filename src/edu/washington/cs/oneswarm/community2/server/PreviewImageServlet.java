package edu.washington.cs.oneswarm.community2.server;

import java.io.IOException;

import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class PreviewImageServlet extends javax.servlet.http.HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static Logger logger = Logger.getLogger(PreviewImageServlet.class.getName());

	public PreviewImageServlet() {
		CommunityDAO.get();
		logger.info("Started PreviewImageServlet");
	}
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			long id = Long.parseLong(request.getParameter("id"));
			
			PublishedSwarmDetails swarmDetails = CommunityDAO.get().getSwarmDetails(id);
			
			if( System.getProperty(EmbeddedServer.Setting.DONT_DISPLAY_PREVIEWS.getKey())
					.equals(Boolean.TRUE.toString()) ) { 
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			
			if( swarmDetails == null ) { 
				logger.warning("Swarm details are null for id: " + id);
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
			
			response.setContentType("image/png");
			response.getOutputStream().write(swarmDetails.getPreviewPNG());
			
		} catch( NumberFormatException e ) {
			logger.warning("Problem during preview generation: " + e.toString());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		} catch (IOException e) {
			logger.warning("Problem during preview generation: " + e.toString());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} 
	}
}
