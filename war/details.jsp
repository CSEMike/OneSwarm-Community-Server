<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN" 
    "http://www.w3.org/TR/html4/loose.dtd">
<HTML>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>
<%@ page import="org.gudy.azureus2.core3.util.*" %>

<%@ page pageEncoding="UTF-8"%>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<%
	long swarmID = Long.parseLong(request.getParameter("id"));
	CommunityAccount user = null;
	boolean canModerate = false, isAdmin = false;
	if( request.getUserPrincipal() != null ) {
		user = dao.getAccountForName(request.getUserPrincipal().getName());
		canModerate = user.canModerate();
		isAdmin = user.isAdmin();
	}
	
	boolean setModerated = false;

	if( canModerate ) { 
		if( request.getParameter("remove") != null ) { 
			dao.markSwarmRemoved(swarmID, true);
		} else if( request.getParameter("unremove") != null ) { 
			dao.markSwarmRemoved(swarmID, false);
		} else if( request.getParameter("delete") != null ) { 
			dao.deleteSwarm(swarmID);
			response.sendRedirect("/");
		} else if( request.getParameter("catPopup") != null ) {
			dao.setSwarmCategory(swarmID, request.getParameter("catPopup"));	
		} else if( request.getParameter("moderated") != null ) {
			dao.setSwarmModerated(swarmID, request.getParameter("moderated").equals("1"));
			setModerated = request.getParameter("moderated").equals("1");
		}
		
		if( request.getMethod().equals("POST") && request.getParameter("editDesc") != null ) { 
			dao.updateDescription(swarmID, request.getParameter("editDesc"));
		}
	} 

	PublishedSwarm swarm = dao.getSwarm(swarmID);
	PublishedSwarmDetails details = dao.getSwarmDetails(swarmID);
	
	if( dao.hasPermissions(user, swarm) == false ) {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		return;
	}
	
	String desc = details.getDescription();
	if( desc == null ) { 
		desc = "(None)";
	} else if( desc.length() == 0 ) { 
		desc = "(None)";
	}
	
	String category = swarm.getCategory() == null ? "(None)" : swarm.getCategory();
%>

<head>
<link title="styles" href="css/community_server.css" type="text/css" rel="stylesheet" media="all"/>
<title>Swarm details: <%= swarm.getName() %></title>

<script language="javascript">
<jsp:include page="/detect_oneswarm.js"/>
</script>

</head>

<body>

<jsp:include page="header.jsp"/>

<a href="javascript:self.history.go(-1)">&laquo; Back</a><br/><br/>

<%	if( canModerate ) { 
	
		long next_unmoderated = dao.getNextUnmoderatedID();
	
		if( swarm.isRemoved() ) { 
	 %>	<h3>This swarm is marked as removed</h3>
		<% } %>

		<div class="moderate">Moderate: 
		
			<% if( swarm.isNeeds_moderated() ) { %>
				<a href="details.jsp?id=<%= swarmID %>&moderated=1">Mark moderated</a> |
			<% } else { %>
				<a href="details.jsp?id=<%= swarmID %>&moderated=0">Mark unmoderated</a> |
			<% } %>
		
		<% if( swarm.isRemoved() == false ) { %>
		<a href="details.jsp?id=<%= swarmID %>&remove">Mark removed</a> | 
		<% } else { %>
		<a href="details.jsp?id=<%= swarmID %>&unremove">Mark unremoved</a> | 
		<% } %> <a href="details.jsp?id=<%= swarmID %>&delete">Permanently delete</a>
		| <form style="display:inline;" method="GET" action="/details.jsp">
			<input type="hidden" name="id" value="<%= swarmID %>" />
			<select name="catPopup">  
		<%	for( String c : dao.getCategories() ) {
				if( swarm.getCategory() != null && swarm.getCategory().equals(c) ) {
					out.println("<option selected=\"selected\">" + c + "</option>\n");
				} else { 
					out.println("<option>" + c + "</option>\n");
				}
			} %>
  </select> <input type="submit" value="Recategorize" /></form>  
		<% if( next_unmoderated > -1 && setModerated ) { %>
		| <a href="/details.jsp?id=<%= next_unmoderated %>">Next unmoderated</a> 
		<% } %>
		</div><br/><br/>
<%	} else if( swarm.isRemoved() ) { 
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		return;
} %>

<table class="details_table">
<tr>
	<td class="label">Download: </td><td>
	
	<% if( request.getAttribute("oneswarm_running") != null ) { %>
		
		<a href="http://127.0.0.1:29615/#search:id:<%= swarm.getInfohash() %>" target="_top"><c:out value="<%= swarm.getName() %>"/></a>
			<small><a href="oneswarm:?xt=urn:osih:<%= Base32.encode(ByteFormatter.decodeString(swarm.getInfohash())) %>">(magnet)</a></small>
	<% } else if( System.getProperty(EmbeddedServer.Setting.STORE_TORRENTS.getKey()).equals(
			Boolean.TRUE.toString()) ) { %>
	<a href="/dl?id=<%= swarm.getSwarmID() %>"><%= swarm.getName() %></a> 
		<small><a href="oneswarm:?xt=urn:osih:<%= swarm.getInfohash() %>">(magnet)</a></small>
	<% } else { %>	
		<a href="oneswarm:?xt=urn:osih:<%= swarm.getInfohash() %>"><%= swarm.getName() %></a>	
	<% } %>
		</td>
</tr>
<% if( System.getProperty(EmbeddedServer.Setting.DONT_DISPLAY_PREVIEWS.getKey()).equals(Boolean.FALSE.toString()) &&
		details.getPreviewPNG() != null ) { %>
<tr>
	<td class="label">Preview:</td><td><img src="/preview?id=<%= swarm.getSwarmID() %>" alt="<%= swarm.getName() %>""/></td>
</tr>
<% } %>
<tr>
	<td class="label">Category:</td><td><%= category %></td>
</tr>
<tr>
	<td class="label">Description:</td><td><c:out value="<%= desc %>"/>
	
	<% if( canModerate && request.getMethod().equals("GET") && request.getParameter("editDesc") != null ) { %>
		<br />Replace with:
		<form id="descform" name="descform" method="post" action="/details.jsp?id=<%= swarmID %>">
		  <textarea name="editDesc" id="editDesc" cols="65" rows="10"></textarea>
		  <br />
		  <label>
		  <input type="submit" value="Save" />
		  </label>
	</form>
	<% } else if( canModerate ) { %>
		<br/><a href="details.jsp?id=<%= swarmID %>&editDesc"><small>(edit)</small></a>
	<% } %>
	
	</td>
</tr>
<tr>
	<td class="label">Size:</td><td><%=StringTools.formatRate(swarm.getTotalSize())%> (<%= swarm.getFileCount() %> files)</td>
</tr>
<% if( canModerate ) { %> 
<tr>
	<td class="label">Uploaded by:</td><td><% 
		CommunityAccount who = dao.getAccountForID(swarm.getUploadedBy());
		out.print(who.getName());
	%></td>
</tr>
<% }

if( details.getLanguage() != null ) {
	out.println("<tr>\n" + 
		"<td class=\"label\">Language:</td><td>" + details.getLanguage() + "</td>\n" + 
	"</tr>\n");
}
%>

<!--
<tr>  
<td class="label">Quality:</td><td> +<%= details.getUpvotes() %> upvotes / -<%= details.getDownvotes() %> downvotes</td>
</tr>
-->
</table>

<h3>Comments</h3>
<jsp:include page="<%= \"comments.jsp?id\" + swarmID %>" />

 <jsp:include page="footer.jsp"/>

</BODY>
</HTML>
