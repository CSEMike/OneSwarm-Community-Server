<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ page pageEncoding="UTF-8"%>

<%@ page import="edu.washington.cs.oneswarm.community2.server.*" %>
<%@ page import="java.util.*" %>
<%@ page import="edu.washington.cs.oneswarm.community2.utils.*" %>


<%!
	public String collect( String [] arr ) {
		StringBuilder outsb = new StringBuilder();
		for( String s : arr ) { 
			outsb.append(s + " ");
		}
		return outsb.toString().trim();
	}

	final CommunityDAO dao = CommunityDAO.get();
	
	final Comparator<CommunityAccount> by_name = new Comparator<CommunityAccount>(){
		public int compare(CommunityAccount o1, CommunityAccount o2)  {
			return o1.getName().compareTo(o2.getName());
		}	
	};
	
	final Comparator<CommunityAccount> by_registrations = new Comparator<CommunityAccount>(){
		public int compare(CommunityAccount o1, CommunityAccount o2)  {
			return o1.getRegistrations() - o2.getRegistrations();
		}	
	};
	
	final Comparator<CommunityAccount> by_uid = new Comparator<CommunityAccount>(){
		public int compare(CommunityAccount o1, CommunityAccount o2)  {
			if( o1.getID() > o2.getID() ) { 
				return 1;
			} else if( o1.getID() < o2.getID() ) { 
				return -1;
			} else { 
				return 0;
			}
		}
	};
	
	final Comparator<CommunityAccount> by_roles = new Comparator<CommunityAccount>() {
		public int compare(CommunityAccount o1, CommunityAccount o2)  {
			StringBuilder o1s = new StringBuilder();
			for( String s : o1.getRoles() ) { 
				o1s.append(s);
			}
			StringBuilder o2s = new StringBuilder();
			for( String s : o2.getRoles() ) { 
				o2s.append(s);
			}
			return o1s.toString().compareTo(o2s.toString());
		}
	};
%>

<%
	final int MAX_RESULTS = 30;

	int offset = request.getParameter("offset") != null ? Integer.parseInt(request.getParameter("offset")) : 0;
	String by = request.getParameter("by");
	List<CommunityAccount> accounts = dao.getAccounts(); 
	StringBuilder curr = new StringBuilder();
	curr.append("admin.jsp?users&offset=");
	curr.append(offset);
	String flipDesc = request.getParameter("desc") != null ? "&desc" : "&asc";
	curr.append(flipDesc);
	Comparator<CommunityAccount> comp = null;
	String currAsc = flipDesc, nextAsc = (flipDesc.equals("&asc") ? "&desc" : "&asc");
	
	if( by != null ) {
		if( by.equals("name") ) { 
			comp = by_name;
		} else if( by.equals("registrations") ) { 
			comp = by_registrations;
		} else if( by.equals("uid") ) { 
			comp = by_uid;
		} else if( by.equals("roles") ) {
			comp = by_roles;
		}
	}
	if( comp != null ) {
		Collections.sort(accounts, comp);
		
		if( flipDesc.equals("&desc") ) { 
			Collections.reverse(accounts);
		} 
	}
	%>
	
	<br/><br/>
	Results: <%= offset %> through <%= Math.min(offset + MAX_RESULTS, accounts.size()) %>
	
	<br/>
	<% if( offset > 0 || offset + MAX_RESULTS < accounts.size() ) { %> 
		<div class="searchnav">
		<%	if( offset > 0 && accounts.size() + offset < MAX_RESULTS ) { %>
				<a href="<%= curr.toString().replace("offset=" + offset, "offset=" + (Math.max(0, offset-MAX_RESULTS))) %>">&laquo; Previous</a> | 
				<a href="<%= curr.toString().replace("offset=" + offset, "offset=" + (Math.min(offset+MAX_RESULTS, accounts.size()-MAX_RESULTS))) %>">Next &raquo;</a>
		<% 	} else if( offset == 0 ) { %>
				&laquo; Previous | 
				<a href="<%= curr.toString().replace("offset=" + offset, "offset=" + (Math.min(accounts.size()-MAX_RESULTS, offset+MAX_RESULTS))) %>">Next &raquo;</a>
		<% 	} else { %> 
				<a href="<%= curr.toString().replace("offset=" + offset, "offset=" + (Math.max(0, offset-MAX_RESULTS))) %>">&laquo; Previous</a> | Next &raquo;
		<% } %>
		</div>
	<% } %>
	
	
	<table class="accountstable" border="0" width="100%">
		<tr class="header">
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=name">Username</a></td>
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=registrations">Key registrations</a></td>
			<td>Max registrations</td>
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=uid">UID</a></td>
			<td><a href="<%= curr.toString().replace(currAsc, nextAsc) %>&by=roles">Roles</a></td>
			<td>Actions</td>
		</tr>
	
<%	boolean odd = false;
	for( int i=offset; i<Math.min(accounts.size(), offset+MAX_RESULTS); i++ ) {
		CommunityAccount acct = accounts.get(i);
		
		String rolesStr = collect(acct.getRoles());
		odd = !odd;
		%>
		<tr class="<%= odd ? "result_odd" : "result" %>">
			<td><c:out value="<%= acct.getName() %>"/> <small>
				(<a href ="/search.jsp?uid=<%= acct.getID() %>">Swarms</a>) 
			</small></td>
			<td><c:out value="<%= acct.getRegistrations() %>"/></td>
			<td><a href="javascript:edit_maxregs(<%= acct.getID()%>,<%= acct.getMaxRegistrations() %>,'<%= curr%>')"><c:out value="<%= acct.getMaxRegistrations() %>"/></a></td>
			<td><c:out value="<%= acct.getID() %>"/></td>
			<td><a href="javascript:edit_roles(<%= acct.getID() %>,'<%= rolesStr %>','<%= curr %>')"><c:out value="<%= rolesStr %>"/></a></td>
			<td>
			<% if( acct.getID() != 1 ) { %>
			<a href="<%= curr %>&deluser=<%= acct.getID() %>">Delete</a>
			<% } %>
			</td>
		</tr>
	<% } %>
	</table>