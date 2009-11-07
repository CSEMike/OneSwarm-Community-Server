<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page language="java" contentType="text/html; 
         charset=US-ASCII" pageEncoding="US-ASCII"%>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.shared.KeyRegistrationRecord" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>


<%!
	final CommunityDAO dao = CommunityDAO.get();
	
	final Comparator<KeyRegistrationRecord> by_name = new Comparator<KeyRegistrationRecord>(){
		public int compare(KeyRegistrationRecord o1, KeyRegistrationRecord o2)  {
			return o1.getNickname().compareTo(o2.getNickname());
		}	
	};
	
	final Comparator<KeyRegistrationRecord> by_registrant = new Comparator<KeyRegistrationRecord>(){
		public int compare(KeyRegistrationRecord o1, KeyRegistrationRecord o2)  {
			if( o1.getCreatedByID() > o2.getCreatedByID() ) { 
				return 1;
			} else if( o1.getCreatedByID() < o2.getCreatedByID() ) { 
				return -1;
			} else { 
				return 0;
			}
		}	
	};
	
	final Comparator<KeyRegistrationRecord> by_registration_date = new Comparator<KeyRegistrationRecord>(){
		public int compare(KeyRegistrationRecord o1, KeyRegistrationRecord o2)  {
			return o1.getRegisteredDate().compareTo(o2.getRegisteredDate());
		}
	};
	
	final Comparator<KeyRegistrationRecord> by_last_refresh = new Comparator<KeyRegistrationRecord>() {
		public int compare(KeyRegistrationRecord o1, KeyRegistrationRecord o2)  {
			return o1.getLastRefreshedDate().compareTo(o2.getLastRefreshedDate());			
		}
	};
	
	final Comparator<KeyRegistrationRecord> by_ip = new Comparator<KeyRegistrationRecord>() {
		public int compare(KeyRegistrationRecord o1, KeyRegistrationRecord o2)  {
			return o1.getRegistrationIP().compareTo(o2.getRegistrationIP());			
		}
	};
%>

<%
	final int MAX_RESULTS = 30;

	int offset = request.getParameter("offset") != null ? Integer.parseInt(request.getParameter("offset")) : 0;
	String by = request.getParameter("by");
	KeyRegistrationRecord [] keys = dao.getRegisteredKeys(); 
	StringBuilder curr = new StringBuilder();
	curr.append("admin.jsp?keys&offset=");
	curr.append(offset);
	String flipDesc = request.getParameter("desc") != null ? "&desc" : "&asc";
	curr.append(flipDesc);
	Comparator<KeyRegistrationRecord> comp = null;
	String currAsc = flipDesc, nextAsc = (flipDesc.equals("&asc") ? "&desc" : "&asc");
	
	if( by != null ) {
		if( by.equals("name") ) { 
			comp = by_name;
		} else if( by.equals("registrant") ) { 
			comp = by_registrant;
		} else if( by.equals("regdate") ) { 
			comp = by_registration_date;
		} else if( by.equals("lastrefresh") ) {
			comp = by_last_refresh;
		} else if( by.equals("ip") ) { 
			comp = by_ip;
		}
	}
	if( comp != null ) {
		Arrays.sort(keys, comp);
		
		if( flipDesc.equals("&desc") ) { 
			Collections.reverse(Arrays.asList(keys));
		} 
	}
	%>
	
	<br/><br/>
	Results: <%= offset %> through <%= Math.min(offset + MAX_RESULTS, keys.length) %>
	
	<br/>
	<% if( offset > 0 || offset + MAX_RESULTS < keys.length ) { %> 
		<div class="searchnav">
		<%	if( offset > 0 && keys.length + offset < MAX_RESULTS ) { %>
				<a href="<%= curr.toString().replace("offset=" + offset, "offset=" + (Math.max(0, offset-MAX_RESULTS))) %>">&laquo; Previous</a> | 
				<a href="<%= curr.toString().replace("offset=" + offset, "offset=" + (Math.min(offset+MAX_RESULTS, keys.length-MAX_RESULTS))) %>">Next &raquo;</a>
		<% 	} else if( offset == 0 ) { %>
				&laquo; Previous | 
				<a href="<%= curr.toString().replace("offset=" + offset, "offset=" + (Math.min(keys.length-MAX_RESULTS, offset+MAX_RESULTS))) %>">Next &raquo;</a>
		<% 	} else { %> 
				<a href="<%= curr.toString().replace("offset=" + offset, "offset=" + (Math.max(0, offset-MAX_RESULTS))) %>">&laquo; Previous</a> | Next &raquo;
		<% } %>
		</div>
	<% } %>
	
	
	<table class="accountstable" border="0" width="100%">
		<tr class="header">
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=name">Nickname</a></td>
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=registrant">Registered by</a></td>
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=regdate">Registered</a></td>
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=lastrefresh">Last refresh</a></td>
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=ip">Registration IP</a></td>
			<td>Public key</td>
			<td>Actions</td>
		</tr>
	
<%	boolean odd = false;
	for( int i=offset; i<Math.min(keys.length, offset+MAX_RESULTS); i++ ) {
		KeyRegistrationRecord key = keys[i];
		
		odd = !odd;
		%>
		<tr class="<%= odd ? "result_odd" : "result" %>">
			<td><c:out value="<%= key.getNickname() %>"/></td>
			<td><c:out value="<%= key.getCreatedByID() %>"/></td>
			<td><c:out value="<%= StringTools.formatDateAppleLike(key.getRegisteredDate(), true) %>"/></td>
			<td><c:out value="<%= StringTools.formatDateAppleLike(key.getLastRefreshedDate(), true) %>"/></td>
			<td><c:out value="<%= key.getRegistrationIP() %>"/></td>
			<td><a href="javascript:window.alert('<%= key.getBase64PublicKey() %>')">Show</a></td>
			<td>
			<a href="<%= curr %>&delkey=<%= key.getID() %>">Delete</a>
			</td>
		</tr>
	<% } %>
	</table>