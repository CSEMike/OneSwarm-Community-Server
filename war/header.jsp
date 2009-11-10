<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.net.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>
<%!
	CommunityDAO dao = CommunityDAO.get();
	final boolean DEFAULT_ALLOW_CHAT_ON_SUBSCRIBE = false;
	
	final boolean DEFAULT_CONFIRM_UPDATES = false;
	final boolean DEFAULT_SYNC_DELETES = true;
	final int DEFAULT_PRUNING_THRESHOLD = 50;
	
	final String REGISTRATION_SERVLET = "/community/";
%>

<% session.setAttribute("pageRenderStarted", new Long(System.currentTimeMillis())); 

	CommunityAccount user = null;
	boolean canModerate = false, isAdmin = false; 
	if( request.getUserPrincipal() != null ) {
		user = dao.getAccountForName(request.getUserPrincipal().getName());
		canModerate = user.canModerate();
		isAdmin = user.isAdmin();
	}
	
	String baseURL = null;
	try { 
		URL url = new URL(request.getRequestURL().toString());
		baseURL = url.getProtocol() + "://" + url.getHost() + (url.getPort() == -1 ? "" : (":" + url.getPort()));
	} catch( Exception e ) {}
	
	boolean allowSignups = System.getProperty(EmbeddedServer.Setting.ALLOW_SIGNUPS.getKey()).equals( 
			Boolean.TRUE.toString()) || isAdmin;
	
%>

  <a href="/"><img align="right" style="position:relative" border="0" src="/img/community_logo.png"/></a>
  
  <%
  boolean oneswarm_is_running = false;
  boolean hasCookie = false;
  boolean subscribed = false;
  
  if( session.getAttribute("oneswarm_running") != null ) { 
	  oneswarm_is_running = true;
	  hasCookie = true;
	  request.setAttribute("oneswarm_running", Boolean.TRUE.toString());
  }
  if( session.getAttribute("subscribed") != null ) { 
	  subscribed = true;
  }
  
  if( request.getParameter("osclient") != null ) { 
	  subscribed = true;
	  oneswarm_is_running = true;
	  hasCookie = true;
	  
	  /**
	   * there seems to be problems mixing SSL and cookies in iframes in some browsers, 
	   * so store these details on the server
	   */
	  session.setAttribute("oneswarm_running", Boolean.TRUE.toString());
	  session.setAttribute("subscribed", Boolean.TRUE.toString());
	  request.setAttribute("oneswarm_running", Boolean.TRUE);
	  
  } else if( request.getCookies() != null ) {
	  for( Cookie c : request.getCookies() ) {
		  if( c.getName().equals("subscribed") ) { 
			  subscribed = true;
		  }
		  if( c.getName().equals("oneswarm_running") ) { 
			  hasCookie = true;
			  oneswarm_is_running = true;
			  request.setAttribute("oneswarm_running", Boolean.TRUE);
		  }
	  }
  }
  
  if( request.getParameter("osclient") == null && hasCookie == false ) { %>
  <div class="no_oneswarm">
  	Downloading files from this site requires <a href="http://oneswarm.cs.washington.edu/">OneSwarm</a>, a 
  	privacy-preserving P2P software client. <a href="http://oneswarm.cs.washington.edu/download.html">Download now.</a> 
  </div>
  <% } else if( request.getParameter("osclient") == null && oneswarm_is_running && !subscribed ) { %>
  	<div class="no_oneswarm"><strong><a target="_blank" href="http://127.0.0.1:29615/#addcserver:<%= 
  					URLEncoder.encode(baseURL,"UTF-8") + ":" + 
  					URLEncoder.encode(System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()), "UTF-8") + ":" + 
					new Boolean(System.getProperty(EmbeddedServer.StartupSetting.REQUIRE_AUTH_FOR_KEY_REGISTRATION.getKey())).toString() + ":" + 
					DEFAULT_CONFIRM_UPDATES + ":" + 
					DEFAULT_SYNC_DELETES + ":" + 
					DEFAULT_PRUNING_THRESHOLD + ":" + 
					"false" + ":" + 
					URLEncoder.encode(System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()), "UTF-8") + ":" + 
					URLEncoder.encode(REGISTRATION_SERVLET, "UTF-8") + ":" + 
					DEFAULT_ALLOW_CHAT_ON_SUBSCRIBE %>">Subscribe to this server</a></strong> 
  	</div>
  <% }  %>  

<div id="searchheader">
  <form method="post" action="/search.jsp">
 <input width="350px" style="vertical-align:middle" 
 		name="search" type="text" value="Community server search" 
 		onFocus="this.value='';this.style.color='black'" 
        onBlur="this.value='Community server search';this.style.color='gray'"
        size="22" class="searchheader">
 <input type="submit" value="Search" method="GET" class="button">
  </form>
</div>


<% if( request.getRequestURI().endsWith("logon.jsp") == false ) { %>
	<div class="login">
	<% if( request.getUserPrincipal() == null ) {
		%>	<a href="/logon.jsp">Login</a> <% 
			if( allowSignups ) { %>
				| <a href="/signup.jsp">Sign up</a>
	<% 		}
	   } else { %>
		User: <%= request.getUserPrincipal().getName() %> <a href="/logout.jsp">(Logout)</a> | <a href="/changepw.jsp">Change password</a>  
		
		<%= isAdmin ? "| <a href=\"/admin/admin.jsp\">Admin UI</a>" : "" %>
		
	<% } %>
	</div>
<% } %>

<%
	List<String> categories = dao.getCategories();
%>
<br/>
<div class="headermenu">
<a href="/">Home</a> | 
<% for( int i=0; i<categories.size(); i++ ) { 
	String category = categories.get(i);
%>
<a href="/search.jsp?cat=<c:out value="<%= category %>"/>"><%= category %></a>
<%
	if( i < categories.size() - 1 ) { 
		out.print("|");
	}
} %>
</div>

<br/>
