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
<title><%= System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) %></title></head>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<BODY>

<jsp:include page="header.jsp"/>

<h2>Invalid username or password.</h2>

 <jsp:include page="footer.jsp"/>
 
</BODY>
</HTML>