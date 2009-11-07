<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<html>
<head>
<link title="styles" href="css/community_server.css" type="text/css" rel="stylesheet" media="all"/>
<title>OneSwarm Community Server Login</title></head>

<jsp:include page="header.jsp"/>

<br><br>

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



</html>
