<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page pageEncoding="UTF-8"%>
         
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN" 
    "http://www.w3.org/TR/html4/loose.dtd">
<HTML>

<head>

<script>
<jsp:include page="/post.js"/>

function edit_roles( uid, existing, curr ) {
	var outRoles = window.prompt('Update role: (admin, moderator, user, bozo)', existing)
	if( outRoles ) {
		post_to_url(curr, {'newroles':outRoles, 'uid':uid, 'ref':curr}, 'GET') 
	}
}

function edit_maxregs( uid, howmany, curr ) {
	var outRegs = window.prompt('Maximum key registrations:', howmany)
	if( outRegs ) {
		post_to_url(curr, {'maxregs':outRegs, 'uid':uid, 'ref':curr}, 'GET')
	}
}

function reset_password( uid ) {
	var outPW = window.prompt('Reset password to:', '')
	if( outPW ) {
		post_to_url('admin.jsp', {'users':'1', 'uid':uid, 'newPass':outPW}, 'GET')
	}
}
</script>

<link title="styles" href="../css/community_server.css" type="text/css" rel="stylesheet" media="all"/>
<title><%= System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) %>: Administration</title></head>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.server.EmbeddedServer" %>

<%!
	final CommunityDAO dao = CommunityDAO.get();
%>

<%
	CommunityAccount user = null;
	boolean canModerate = false, isAdmin = false;
	if( request.getUserPrincipal() != null ) {
		user = dao.getAccountForName(request.getUserPrincipal().getName());
		canModerate = user.canModerate();
		isAdmin = user.isAdmin();
	}
	
	/**
	 * Just in case the web.xml config isn't quite right.
	 */
	if( !isAdmin ) { 
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		return;
	}
	
	/**
	 * Take care of actions first. 
	 */ 
	if( request.getParameter("newPass") != null ) {
		try {
			long uid = Long.parseLong(request.getParameter("uid"));
			if(  uid > 0 ) {
				dao.changePassword(dao.getAccountForID(uid).getName(), request.getParameter("newPass"));
			} else { 
				throw new Exception("Bad UID: " + uid);
			}
		} catch( Exception e ) {
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;	
		}
	} 
	if( request.getParameter("newroles") != null ) { 
		String newrole = request.getParameter("newroles");
		dao.updateRole(Long.parseLong(request.getParameter("uid")), newrole);
	}
	if( request.getParameter("maxregs") != null ) {
		try { 
			int neu = Integer.parseInt(request.getParameter("maxregs"));
			long uid = Long.parseLong(request.getParameter("uid"));
			dao.setMaxRegistrationsForUID(neu, uid);
		} catch( NumberFormatException e ) {
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
	if( request.getParameter("deluser") != null ) {
		long id = Long.parseLong(request.getParameter("deluser"));
		if( id != 1 ) {
			dao.deleteAccount(dao.getAccountForID(id).getName());
		}
	}
	if( request.getParameter("delkey") != null ) {
		long id = Long.parseLong(request.getParameter("delkey"));
		dao.deregisterKey(dao.getKeyForID(id).getBase64PublicKey());
	}
	
	if( request.getParameter("delcat") != null ) {
		dao.deleteCategory(request.getParameter("delcat"));
	}
	
	if( request.getMethod().equals("POST") ) {
		
		if( request.getParameter("newCatName") != null ) {
			dao.addCategory(request.getParameter("newCatName"));
		} else { 
			Map params = request.getParameterMap();
			for( String key : (Set<String>)params.keySet() ) { 
				for( EmbeddedServer.Setting setting : EmbeddedServer.Setting.values() ) { 
					if( (setting.getKey()+".val").equals(key) ) { 
						System.setProperty(setting.getKey(), request.getParameter(key));
					}
				}
			} // for over params
		} // else new category
	}
	 
	/**
	 * Redirect for forms when required
	 */
	 if( request.getParameter("ref") != null ) {
		 response.sendRedirect(request.getParameter("ref"));
	 }
%>

<BODY>

<jsp:include page="/header.jsp"/>

<jsp:include page="header.jsp"/>

<% if( request.getParameter("users") != null ) { %> 
	<jsp:include page="users.jsp"/>
<% } else if( request.getParameter("stats") != null ) { %>
	<jsp:include page="stats.jsp"/>
<% } else if( request.getParameter("settings") != null ) { %>
	<jsp:include page="settings.jsp"/>
<% } else if( request.getParameter("keys") != null ) { %>
	<jsp:include page="keys.jsp"/>
<% } else if( request.getParameter("categories") != null ) { %>
	<jsp:include page="categories.jsp"/>
<% } %>

<jsp:include page="/footer.jsp"/>
 
</BODY>
</HTML>
