<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<head>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.shared.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>
<%@ page import="nl.captcha.Captcha" %>

<link title="styles" href="../css/community_server.css" type="text/css" rel="stylesheet" media="all"/>
<title><%= System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) %>: Batch account creation</title></head>
</head>

<jsp:include page="/header.jsp"/>

<jsp:include page="header.jsp"/>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<% 
if( request.getMethod().equals("POST") ) {
	boolean canModerate = false, isAdmin = false;
	CommunityAccount user = null;
	if( request.getUserPrincipal() != null ) {
		user = dao.getAccountForName(request.getUserPrincipal().getName());
		canModerate = user.canModerate();
		isAdmin = user.isAdmin();
	}
	
	if( !isAdmin ) {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		return;
	}
	
	request.setCharacterEncoding("UTF-8");
	
	String accounts = request.getParameter("accounts");
	if( accounts == null ) { 
		System.err.println("[WARNING]: Null accounts parameter in batch account creation request.");
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		return;
	}
	
	BufferedReader byLines = new BufferedReader(new StringReader(accounts));
	while( true ) {
		String line = byLines.readLine();
		if( line == null ) {
			break;
		}
		try {
			String [] toks = line.split(",");
			
			if( toks.length != 2 ) {
				throw new IOException("Malformed line (one username, password pair per line)");
			}
			
			String username = toks[0].trim();
			String password = toks[1];
			
			if( username.length() > 64 || username.length() < 3 ) { 
				throw new IOException("Bad username length: " + username.length()); 
			}
			
			if( password.length() < 4 || password.length() > 255 ) { 
				throw new IOException("Bad password length: " + password.length());
			}
			
			dao.createAccount(username, password, CommunityDAO.UserRole.USER);
			
			out.println("<br/>Create account: " + username);
			
		} catch( Exception e ) {
			out.println("<br/>Error when parsing line: " + e.toString() + " (" + line + ")");
			continue;
		}	
	}
	out.println("<br/>");
}
%>

<br/><br/>Enter usernames and passwords, one per line, separated by a comma. For example: 
<pre>
test user, some_password
</pre>

and click 'Create' to create user accounts. 

<form method="post" action="batch_accounts.jsp">
	  <textarea name="accounts" id="accounts" cols="65" rows="10"></textarea>
	  <br />
	  <label>
	  <input type="submit" value="Create" />
	  </label>
</form>

</html>
