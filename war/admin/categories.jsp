<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page language="java" contentType="text/html; 
         charset=US-ASCII" pageEncoding="US-ASCII"%>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.shared.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.URLUTF8Encoder" %>
<%@ page import="java.util.*" %>

<%!
	CommunityDAO dao = CommunityDAO.get();
%>

<h2>Categories</h2>

<form name="newCat" method="post" action="admin.jsp">
	<input type="hidden" name="ref" value="<%= "admin.jsp?categories" %>"/>
  <label>Add new: 
  <input type="text" name="newCatName" />
  </label>
  <input type="submit" value="Submit" />
</form>

<ul>
<% for( String cat : dao.getCategories() ) { %>
<li>
	<%= cat %> <small><a href="admin.jsp?delcat=<%= URLUTF8Encoder.encode(cat) %>&ref=<%= "admin.jsp?categories" %>">(Delete)</a></small> 
</li>
<% } %>
</ul>