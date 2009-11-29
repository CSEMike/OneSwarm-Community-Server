<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.URLUTF8Encoder" %>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<%
	int offset = request.getParameter("offset") == null ? 0 : Integer.parseInt(request.getParameter("offset"));
	String keys = request.getParameter("search");
	String category = request.getParameter("cat");
	String sortBy = request.getParameter("by");
	
	int maxResults = Integer.parseInt(System.getProperty(EmbeddedServer.Setting.SWARMS_PER_PAGE.getKey()));
	// applies for keyword search -- not category. 
	if( keys != null ) { 
		maxResults = Integer.parseInt(System.getProperty(EmbeddedServer.Setting.SWARMS_PER_SEARCH.getKey()));
	}
	
	if( sortBy == null ) { 
		sortBy = "date_uploaded";
	}
	
	CommunityAccount user = null;
	boolean canModerate = false;
	if( request.getUserPrincipal() != null ) {
		user = dao.getAccountForName(request.getUserPrincipal().getName());
		canModerate = user.canModerate();
	}
	
	Long uid = null;
	if( canModerate && request.getParameter("uid") != null ) { 
		uid = Long.parseLong(request.getParameter("uid"));
	}
	
	List<PublishedSwarm> swarms = dao.selectSwarms(keys, category, uid, offset, maxResults, sortBy, 
			request.getParameter("desc") != null ? true : false, canModerate );

	String flipDesc = request.getParameter("desc") != null ? "&asc" : "&desc";

	String curr = "search.jsp?" + 
			(keys != null ? "search=" + URLUTF8Encoder.encode(keys) + "&" : "") + 
			(category != null ? "cat=" + URLUTF8Encoder.encode(category) + "&" : "") +
			"offset=" + offset + flipDesc;
%>

<%@ page pageEncoding="UTF-8"%>

<head>
<link title="styles" href="css/community_server.css" type="text/css" rel="stylesheet" media="all"/>

<% if( category != null ) { %>
	<link rel="alternate" type="application/rss+xml" title="<c:out value="<%= System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) %>"/>: <c:out value='<%= category %>'/>" href="/rss?cat=<c:out value='<%= category %>'/>" />
<% } %>

<title>Search results: <c:out value="<%= keys %>"/></title></head>

<body>


<jsp:include page="header.jsp"/>


	<% if( keys != null && keys.length() < 3 ) { %>
		<p>Search query must be at least 3 characters in length.</p>
	<% } else { %>
	
	<% if( uid != null ) { 
		CommunityAccount target = dao.getAccountForID(uid);
		%> 
		<h3>Swarms by user: <%= target.getName() %></h3>
	<% } %> 
	
	<div class="results">Results: <%= keys != null ? keys : "" %> (<%= offset %> through <%= offset + swarms.size() %>)</div>
	
	<% if( offset > 0 || swarms.size() == maxResults ) { %> 
		<div class="searchnav">
		<%	if( offset > 0 && swarms.size() == maxResults ) { %>
				<a href="<%= curr.replace("offset=" + offset, "offset=" + (Math.max(0, offset-maxResults))) %>">&laquo; Previous</a> | 
				<a href="<%= curr.replace("offset=" + offset, "offset=" + (Math.min(offset + swarms.size(), offset+maxResults))) %>">Next &raquo;</a>
		<% 	} else if( offset == 0 ) { %>
				&laquo; Previous | 
				<a href="<%= curr.replace("offset=" + offset, "offset=" + (Math.min(offset + swarms.size(), offset+maxResults))) %>">Next &raquo;</a>
		<% 	} else { %> 
				<a href="<%= curr.replace("offset=" + offset, "offset=" + (Math.max(0, offset-maxResults))) %>">&laquo; Previous</a> | Next &raquo;
		<% } %>
		</div>
	<% } %>
	
	<table class="swarmstable" border="0" width="100%">
		<tr class="header">
			<td><a href="<%= curr %>&by=name">Swarm name</a></td>
			<td><a href="<%= curr %>&by=total_size">Size<a></td>
			<td><a href="<%= curr %>&by=category">Category<a></td>
			<td><a href="<%= curr %>&by=date_uploaded">Date uploaded</a></td>
		</tr>
		
<%	boolean odd = false;
	for( PublishedSwarm swarm : swarms ) {
		if( swarm.isRemoved() && !canModerate ) {
			continue;
		}
		
		odd = !odd;
		
		if( swarm.isNeeds_moderated() ) {  %>
			<tr class="unmoderated">
		<% } else { %>
			<tr class="<%= odd ? "result_odd" : "result" %>">
		<% } %>
			<td><a href="/details.jsp?id=<%=swarm.getSwarmID()%>">
				<%= (swarm.isRemoved() && canModerate) ? "<strike>" : "" %> 
				<c:out value="<%= swarm.getName() %>"/></a>
				<%= (swarm.isRemoved() && canModerate) ? "</strike> (removed)" : "" %> 
				</td>
			<td><c:out value="<%= StringTools.formatRate(swarm.getTotalSize()) %>"/></td>
			<td><c:out value="<%= swarm.getCategory() == null ? \"None\" : swarm.getCategory() %>"/></td>
			<td><c:out value="<%= StringTools.formatDateAppleLike(new Date(swarm.getUploadedTimestamp()), true) %>"/></td>
		</tr>
	<% } %>
	</table>
	
	<%}%>
	
	<% if( offset > 0 || swarms.size() == maxResults ) { %> 
		<div class="searchnav">
		<%	if( offset > 0 && swarms.size() == maxResults ) { %>
				<a href="<%= curr.replace("offset=" + offset, "offset=" + (Math.max(0, offset-maxResults))) %>">&laquo; Previous</a> | 
				<a href="<%= curr.replace("offset=" + offset, "offset=" + (Math.min(offset + swarms.size(), offset+maxResults))) %>">Next &raquo;</a>
		<% 	} else if( offset == 0 ) { %>
				&laquo; Previous | 
				<a href="<%= curr.replace("offset=" + offset, "offset=" + (Math.min(offset + swarms.size(), offset+maxResults))) %>">Next &raquo;</a>
		<% 	} else { %> 
				<a href="<%= curr.replace("offset=" + offset, "offset=" + (Math.max(0, offset-maxResults))) %>">&laquo; Previous</a> | Next &raquo;
		<% } %>
		</div>
	<% } %>
	
<jsp:include page="footer.jsp"/>

</BODY>
</HTML>
