package edu.washington.cs.oneswarm.community2.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

public class UpdatePreviewServlet extends javax.servlet.http.HttpServlet {
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(UpdatePreviewServlet.class.getName());
	
	long loadTime = 0;
	
	public UpdatePreviewServlet() {
		CommunityDAO.get();
		logger.info("Preview update servlet created.");
	}
		
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		
		logger.finest("got post: " + request.toString());
		
		// only moderators / admins can update previews. 
		if( request.getUserPrincipal() == null ) { 
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		
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
		
		if( ServletFileUpload.isMultipartContent(request) == false ) { 
			logger.warning("Got a POST to the preview update servlet that is not multi-part, dropping.");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		FileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		
		upload.setFileSizeMax(5*1048576);
		
		PrintStream out = null;
		
		try {
			List<FileItem> items = upload.parseRequest(request);
			out = new PrintStream(response.getOutputStream());
			
			long id = -1L;
			byte [] previewpng = null;
			
			for( FileItem f : items ) { 
				logger.info("field name: " + f.getFieldName() + " name: " + f.getName() + " " + f.getSize() );
				
				if( f.getFieldName().equals("id") ) { 
					id = Long.parseLong(f.getString());
				} else if( f.getFieldName().equals("previewpng") ) { 
					InputStream in = f.getInputStream();
					previewpng = new byte[in.available()];
					f.getInputStream().read(previewpng);	
				} else {
					throw new IOException("Unrecognized field name: " + f.getFieldName());
				}
			}
			
			if( id < 0 ) {
				throw new IOException("Missing parameter: id");
			}
			
			if( previewpng.length > 0 ) { 
				CommunityDAO.get().update_preview(id, previewpng);	
			} else { 
				CommunityDAO.get().update_preview(id, null);
			}
			
			response.sendRedirect("/details.jsp?id=" + id);
			
		} catch (FileUploadException e) {
			logger.warning(e.toString());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

			out.println("Bad request -- " + e.toString());
			
		} catch (IOException e) {
			logger.warning(e.toString());
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			
			out.println("Bad request -- " + e.toString());
			
		} finally { 
			try {
				out.flush();
				out.close();
			} catch( Exception e ) {}
		}
	}

}
