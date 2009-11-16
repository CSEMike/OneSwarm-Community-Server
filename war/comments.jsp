<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<%
	if( request.getMethod().equals("POST") && request.getParameter("comment") != null ) {
		
		if( System.getProperty(EmbeddedServer.Setting.DISABLE_COMMENTS.getKey()).equals(
				Boolean.TRUE.toString()) ) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		
		try {
			dao.postComment(request.getUserPrincipal().getName(), 
					Long.parseLong(request.getParameter("id")), 
					request.getParameter("comment"), 
					request.getParameter("replyTo") == null ? 0 : Long.parseLong(request.getParameter("replyTo")),  
					request.getRemoteAddr());
			
		} catch( IOException e ) { 
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
	}
%>

<%
	CommunityAccount user = null;
	if( request.getUserPrincipal() != null ) {
		user = dao.getAccountForName(request.getUserPrincipal().getName());
	}
	
	/**
	 * Moderators can delete any comment. Users can delete their own comments.
	 */
	if( request.getParameter("del") != null ) {
		long comment_id = Long.parseLong(request.getParameter("del"));
		if( user.canModerate() ) { 
			dao.removeComment(comment_id);
		} else { 
			Comment c = dao.getComment(comment_id);
			if( c.getAccountName().equals(user.getName()) ) { 
				dao.removeComment(comment_id);
			}
		}
	}

	long swarmID = Long.parseLong(request.getParameter("id"));
	PublishedSwarm swarm = dao.getSwarm(swarmID);
	
	if( dao.hasPermissions(user, swarm) == false ) { 
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		return;
	}
	
	int offset = request.getParameter("off") == null ? 0 : Integer.parseInt(request.getParameter("off"));
	List<Comment> comments = dao.selectComments(swarmID, offset, 30);
%>

<% if( comments.size() > 0 ) { %>
<table class="comments" border="1" cellpadding="0" cellspacing="0">
	<% for( Comment c : comments ) { %>
		
		<tr valign="top">
			<td class="from"><c:out value="<%= c.getAccountName() %>"/>
			<br/><small>
			<%=StringTools.formatDateAppleLike(new Date(c.getTimestamp()), true)%>
			
			<%
			if( user != null ) { 
				if( user.canModerate() || c.getAccountName().equals(user.getName()) ) { %> 
				<br/>
				<a href="details.jsp?id=<%= swarmID %>&del=<%= c.getCommentID() %>">(Delete)</a><br/>
				<%= c.getIp() %>
			<%	}
			}
			%>
			
		</small>
		</td> 
		<td class="comment"><pre><c:out value="<%=c.getComment()%>"/></pre></td></tr>
		
	<% } %>	
	
</table>
<% } else { %>
<div>(None)</div><br/>
<% } %>

<% 
if( System.getProperty(EmbeddedServer.Setting.DISABLE_COMMENTS.getKey()).equals(
		Boolean.FALSE.toString()) ) {
	if( request.getUserPrincipal() != null ) { %>
	<br />Comment on: <%= swarm.getName() %>
	<form id="commentform" name="commentform" method="post" action="/details.jsp?id=<%= swarmID %>">
	  <textarea name="comment" id="comment" cols="65" rows="10"></textarea>
	  <br />
	  <label>
	  <input type="submit" name="Submit" id="Submit" value="Submit" />
	  </label>
</form>
<% } else { %>
	<div class="loginreq">You must <a href="/logon.jsp">log in</a> to make comments</div>
<% } 
} else { %>
	<br/>Comments are disabled.
<% } %>
