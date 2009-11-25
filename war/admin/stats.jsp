<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page pageEncoding="UTF-8"%>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.shared.*" %>

<%@ page import="java.util.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<br/><br/>
<table>
<tr>
<td>Key refreshes (last 30 mins):</td><td>
<% 
	KeyRegistrationRecord [] registeredKeys = dao.getRegisteredKeys();
	Date thirtyMins = new Date(System.currentTimeMillis() - (30 * 60 * 1000));
	int active = 0;
	for( KeyRegistrationRecord k : registeredKeys ) {
		if( k.getLastRefreshedDate().after(thirtyMins) ) { 
			active++;
		}
	}
	out.print(active);
%></td>
</tr>
<tr>
<td>Total accounts:</td><td><%= dao.getAccounts().size() %></td> 
</tr>
<tr>
<td>Total registered keys:</td><td><%= registeredKeys.length %></td> 
</tr>
<tr>
<td>Total swarms: </td><td><%= dao.getApproximateRowCount("published_swarms") %> (including hidden)</td>
</tr>
<tr>
<td>Total comments: </td><td><%= dao.getApproximateRowCount("comments") %></td>
</tr>
</table>