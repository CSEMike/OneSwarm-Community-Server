package edu.washington.cs.oneswarm.community2.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.mysql.jdbc.exceptions.MySQLIntegrityConstraintViolationException;

public class SwarmPublishServlet extends javax.servlet.http.HttpServlet {
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(SwarmPublishServlet.class.getName());
	
	long loadTime = 0;
	
	public SwarmPublishServlet() {
		CommunityDAO.get();
		logger.info("Swarm publishing servlet created.");
	}
		
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		
		logger.finest("got post: " + request.toString());
		
		if( request.getUserPrincipal() == null && 
			System.getProperty(EmbeddedServer.StartupSetting.REQUIRE_AUTH_FOR_PUBLISH.getKey()).equals(Boolean.TRUE.toString()) ) { 
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		
		if( System.getProperty(EmbeddedServer.Setting.ALLOW_USER_PUBLISHING.getKey()).equals( 
				Boolean.FALSE.toString()) ) {
			
			boolean canModerate = false, isAdmin = false;
			if( request.getUserPrincipal() != null ) {
				CommunityAccount user = CommunityDAO.get().getAccountForName(request.getUserPrincipal().getName());
				canModerate = user.canModerate();
				isAdmin = user.isAdmin();
			}
			
			if( canModerate == false && isAdmin == false ) { 
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
		}
		
		if( ServletFileUpload.isMultipartContent(request) == false ) { 
			logger.warning("Got a POST to the publish servlet that is not multi-part, dropping.");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		
		upload.setFileSizeMax(5*1048576);
		
		try {
			List<FileItem> items = upload.parseRequest(request);
			
			String description = null;
			byte [] torrentbin = null;
			byte [] previewpng = null;
			String category = null;
			
			for( FileItem f : items ) { 
				logger.info("field name: " + f.getFieldName() + " name: " + f.getName() + " " + f.getSize() );
				
				if( f.getFieldName().equals("commentstr") ) { 
					description = f.getString();
				} else if( f.getFieldName().equals("torrentbin") ) {
					InputStream in = f.getInputStream();
					torrentbin = new byte[in.available()];
					f.getInputStream().read(torrentbin);
				} else if( f.getFieldName().equals("previewpng") ) { 
					InputStream in = f.getInputStream();
					previewpng = new byte[in.available()];
					f.getInputStream().read(previewpng);	
				} else if( f.getFieldName().equals("categorystr") ) {
					 category = f.getString();
					 
					 // need to validate this against our list of keywords. client may not respect. 
					 if( CommunityDAO.get().getCategories().contains(category) == false ) {
						 logger.warning("Client offered bad category keyword: " + category + " / " + request.getRemoteAddr());
						 category = null; 
					 }
					 
				} else {
					throw new IOException("Unrecognized field name: " + f.getFieldName());
				}
			}
			
			boolean shouldAttribute = true; 
			
			try { 
				shouldAttribute = Boolean.parseBoolean(System.getProperty(EmbeddedServer.Setting.RETAIN_ACCOUNT_INFO.getKey()));
			} catch( Exception e ) { 
				e.printStackTrace();
			}
			
			CommunityDAO.get().publish_swarm(torrentbin, previewpng, description, category,  
					shouldAttribute ? (CommunityAccount) request.getUserPrincipal() : null, 
					request.getRemoteAddr()); 
			
		} catch( DuplicateSwarmRegistrationException e ) {
			logger.warning(e.toString());
			response.setStatus(HttpServletResponse.SC_CONFLICT);
		} catch (FileUploadException e) {
			logger.warning(e.toString());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		} catch (IOException e) {
			logger.warning(e.toString());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}

}
