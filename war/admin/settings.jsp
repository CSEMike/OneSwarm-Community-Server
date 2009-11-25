<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page pageEncoding="UTF-8"%>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.shared.*" %>

<%@ page import="java.util.*" %>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<div class="settingsWarning">
Changes made to runtime settings do not update the community configuration file and will be <br/>
reverted when the server is restarted. To make these changes permanent, you'll <br/>
need to update the configuration on disk. 
</div>

<table class="settingsTable">
<tr><td></td><td></td><td></td><td><strong>Default</strong></td></tr>
<% for( EmbeddedServer.Setting s : EmbeddedServer.Setting.values() ) { %>
<tr>
<td><%= s.getKey() %></td><td>
<form method="post" action="admin.jsp">
<input type="hidden" name="ref" value="<%= "admin.jsp?settings" %>"/>
 <input type="text" name="<%= s.getKey() %>.val" value="<%= System.getProperty(s.getKey()) != null ? System.getProperty(s.getKey()) : "" %>"/>
 <input type="submit" value="Update" method="POST" class="button">
</form>
</td><td><%= s.getHelp() %></td><td><%= s.getDefaultValue().toString() %></td>
</tr>
<% } %>
</table>