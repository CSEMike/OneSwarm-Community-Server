<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page language="java" contentType="text/html; 
         charset=US-ASCII" pageEncoding="US-ASCII"%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN" 
    "http://www.w3.org/TR/html4/loose.dtd">
<HTML>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>

<head>
<link title="styles" href="css/community_server.css" type="text/css" rel="stylesheet" media="all"/>

<link rel="alternate" type="application/rss+xml" title="<c:out value="<%= System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) %>"/>" href="/rss"/>

<title><%= System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) %></title>

<script language="javascript">
<jsp:include page="/detect_oneswarm.js"/>
</script>

</head>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<%
	CommunityAccount user = null;
	boolean canModerate = false, isAdmin = false;
	if( request.getUserPrincipal() != null ) {
		user = dao.getAccountForName(request.getUserPrincipal().getName());
		canModerate = user.canModerate();
		isAdmin = user.isAdmin();
	}
	
	if( request.getParameter("osclient") != null ) { 
		Cookie hasClient = new Cookie("oneswarm-client", request.getParameter("osclient"));
		hasClient.setMaxAge(1209600); // 2 weeks
		response.addCookie(hasClient);
		
		response.addHeader("P3P","CP=\"IDC DSP COR ADM DEVi TAIi PSA PSD IVAi IVDi CONi HIS OUR IND CNT\"");
	}
%>

<BODY>

<jsp:include page="header.jsp"/>

<h2>Most recent swarms</h2>
<table width="100%" border="0" class="swarmstable">
   <tr class="header">
     <td>Swarm name</td>
     <td>Size</td>
     <td>Category</td>
     <td>Date</td>
   </tr>
   
<%
	int offset = request.getParameter("off") == null ? 0 : Integer.parseInt(request.getParameter("off"));
	int limit = request.getParameter("lim") == null ? 20 : Math.min(Integer.parseInt(request.getParameter("lim")), 100);
	
	List<PublishedSwarm> swarms = dao.selectSwarms(null, null, null, offset, limit, "date_uploaded", true, user == null ? false : user.canModerate() );
	boolean odd = false;
	for( PublishedSwarm swarm : swarms ) {
		if( swarm.isRemoved() && !canModerate ) {
			continue;
		}
		
		odd = !odd;
		
		if( swarm.isNeeds_moderated() && System.getProperty(EmbeddedServer.Setting.REQUIRE_SWARM_MODERATION.getKey()).equals(Boolean.TRUE.toString()) ) {  %>
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
 
 <jsp:include page="footer.jsp"/>
 
</BODY>
</HTML>
