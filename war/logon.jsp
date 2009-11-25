<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>

<%@ page pageEncoding="UTF-8"%>

<html>
<head>
<link title="styles" href="css/community_server.css" type="text/css" rel="stylesheet" media="all"/>
<title><%= System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) %>: Login</title></head>

<jsp:include page="header.jsp"/>

<% if( request.getUserPrincipal() != null ) {
	response.sendRedirect("/files.jsp");
}%>

<h3>Login</h3>

<form action="j_security_check" method=post>
<table border="0" cellpadding="5">
  <tr>
    <td width="125px"><div align="right">Username:</div></td>
    <td width="125px"><input type="text" name="j_username" width="150px"></td>
  </tr>
  <tr>
    <td><div align="right">Password: </div></td>
    <td><input type="password" width="150px" name="j_password"></td>
  </tr>
  <tr>
  	<th colspan="2"><input type="submit" value="Log in"></th>
  </tr>
</table>
</form>

<% if( Boolean.parseBoolean(System.getProperty(EmbeddedServer.Setting.ALLOW_SIGNUPS.getKey())) == true ) { %>
Or, <a href="/signup.jsp">create a new account</a>.
<% } %>

</html>
