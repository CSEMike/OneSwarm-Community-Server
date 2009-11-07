package edu.washington.cs.oneswarm.community2.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedOutput;

import edu.washington.cs.oneswarm.community2.utils.StringTools;

public class RSSServlet extends javax.servlet.http.HttpServlet {
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(RSSServlet.class.getName());

	public RSSServlet() {
		logger.info("RSS Generation servlet started.");
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) {

		response.setContentType("application/rss+xml");
		
		final CommunityDAO dao = CommunityDAO.get();
		final String baseURL = System.getProperty(EmbeddedServer.Setting.RSS_BASE_URL.getKey());
		if (baseURL == null) {
			logger.warning("No base URL specified. Cannot generate RSS feed.");
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			return;
		}

		String cat = request.getParameter("cat");
		CommunityAccount user = null;
		boolean canModerate = false, isAdmin = false;
		if (request.getUserPrincipal() != null) {
			user = dao.getAccountForName(request.getUserPrincipal().getName());
			canModerate = user.canModerate();
			isAdmin = user.isAdmin();
		}

		List<PublishedSwarm> swarms = dao.selectSwarms(null, cat, null, 0, Integer.parseInt(System.getProperty(EmbeddedServer.Setting.SWARMS_PER_PAGE.getKey())), "date_uploaded", true, canModerate);

		SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType("rss_2.0");

		feed.setTitle(System.getProperty(EmbeddedServer.Setting.SERVER_NAME.getKey()) + (cat != null ? ": " + cat : ""));

		feed.setLink(baseURL);
		feed.setDescription("");

		List<SyndEntry> entries = new ArrayList<SyndEntry>();
		SyndEntry entry;
		SyndContent description;

		for (PublishedSwarm s : swarms) {

			PublishedSwarmDetails details = dao.getSwarmDetails(s.getSwarmID());

			entry = new SyndEntryImpl();
			entry.setTitle(s.getName());
			entry.setLink(baseURL + "details.jsp?id=" + s.getSwarmID());
			entry.setPublishedDate(new Date(s.getUploadedTimestamp()));
			
			description = new SyndContentImpl();
			description.setType("text/plain");
			description.setValue(details.getDescription() + "\n" + (s.getCategory() != null ? s.getCategory() + " / " : "") + StringTools.formatRate(s.getTotalSize()));
			
			entry.setDescription(description);

			entries.add(entry);
		}

		feed.setEntries(entries);

		SyndFeedOutput output = new SyndFeedOutput();
		PrintWriter pw;
		try {
			pw = new PrintWriter(response.getOutputStream());
			output.output(feed, pw);
			pw.flush();
			pw.close();
		} catch (IOException e) {
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} catch (FeedException e) {
			e.printStackTrace();
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
