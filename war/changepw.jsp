<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN" 
    "http://www.w3.org/TR/html4/loose.dtd">
<HTML>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.IOException" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>

<%@ page pageEncoding="UTF-8"%>

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
	
	if( user == null ) {
		System.err.println("[WARNING]: Got request to change password without a principal.");
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		return;
	}
%>

<head>
<link title="styles" href="css/community_server.css" type="text/css" rel="stylesheet" media="all"/>
<title>Change password: <c:out value="<%= user.getName() %>"/></title>

<script language="javascript">
<jsp:include page="/detect_oneswarm.js"/>
</script>

</head>

<jsp:include page="header.jsp"/>

<%
	if( request.getMethod().equals("POST") ) {
		String username = request.getParameter("username");
		String currPW = request.getParameter("currpassword");
		String newPW = request.getParameter("newpassword");
		String newPW2 = request.getParameter("newpassword2");
		
		CommunityAccount authenticated = (CommunityAccount) dao.authenticate(username, currPW);
		if( authenticated != null && authenticated.getName().equals(username) ) {
			
			if( newPW.equals(newPW2) == false ) { 
				out.println("<h2>Passwords do not match.</h2>");
				out.println("<a href=\"javascript:self.history.go(-1)\">&laquo; Back</a><br/>");
				return;
			} else { 
				out.println("<h2>Password changed.</h2>");				
				dao.changePassword(username, newPW);
				return;
			}
		} else { 
			out.println("<h2>Current password is not valid.</h2>");
			out.println("<a href=\"javascript:self.history.go(-1)\">&laquo; Back</a><br/>");
			return;
		}
	}
%>

<h3>Change password</h3>

<form action="/changepw.jsp" method=post>
<table border="0" cellpadding="5">
  <tr>
    <td width="125px"><div align="right">Username:</div></td>
    <td width="125px"><input type="text" name="username" width="150px" value="<c:out value="<%= user.getName() %>"/>" <%= isAdmin == false ? "readonly" : "" %>></td>
  </tr>
  <tr>
    <td><div align="right">Current password: </div></td>
    <td><input type="password" width="150px" name="currpassword"></td>
  </tr>
  
  <tr>
    <td><div align="right">New password: </div></td>
    <td><input type="password" width="150px" name="newpassword"></td>
  </tr>
  <tr>
    <td><div align="right">Reenter new password: </div></td>
    <td><input type="password" width="150px" name="newpassword2"></td>
  </tr>
  <tr>
  	<th colspan="2"><input type="submit" value="Change password"></th>
  </tr>
</table>
</form>
