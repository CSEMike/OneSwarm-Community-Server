package edu.washington.cs.oneswarm.community2.server;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CategoriesServlet extends javax.servlet.http.HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static Logger logger = Logger.getLogger(CategoriesServlet.class.getName());
	
	public CategoriesServlet() {}
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		try {
			List<String> cats = CommunityDAO.get().getCategories();
			PrintStream out = new PrintStream(response.getOutputStream());
			out.println("<categories>");
			for( String category : cats ) { 
				out.println("<category name=\"" + category + "\"/>");
			}
			out.println("</categories>");
			out.flush();
			out.close();
			
		} catch( IOException e ) { 
			logger.warning(e.toString());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
