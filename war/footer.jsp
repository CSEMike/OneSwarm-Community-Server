<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.shared.CommunityConstants" %>

<%@ page pageEncoding="UTF-8"%>

<div class="footer">
	<% 
		long started = (Long)session.getAttribute("pageRenderStarted");
	%>
	
	<%= (System.currentTimeMillis()-started) %> ms | <a href="http://oneswarm.cs.washington.edu/">OneSwarm</a> Community Server <%= CommunityConstants.VERSION %> 
</div>
