<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page pageEncoding="UTF-8"%>

<html>
<head>
<title>Logging out...</title>
</head>

<%
request.getSession().invalidate();
response.sendRedirect("/");
%>

</html>
