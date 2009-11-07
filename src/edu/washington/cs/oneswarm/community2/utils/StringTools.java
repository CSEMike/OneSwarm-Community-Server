package edu.washington.cs.oneswarm.community2.utils;

import java.util.Date;

public class StringTools {


	public static String formatRate(String inRateStr) {
		return formatRate(Long.parseLong(inRateStr));
	}

	public static String formatRate(long inLongBytes) {
		return formatRate(inLongBytes, "B");
	}

	public static String formatRate(long inBytes2, String unit) {

		double inBytes=(double)inBytes2;
		
		if (inBytes < 1024)
			return trim(inBytes, 0) + " " + unit;

		inBytes /= 1024.0;

		if (inBytes < 1024)
			return trim(inBytes, 0) + " K" + unit;

		inBytes /= 1024.0;

		if (inBytes < 1024)
			return trim(inBytes, 2) + " M" + unit;

		inBytes /= 1024.0;

		if (inBytes < 1024)
			return trim(inBytes, 2) + " G" + unit;

		inBytes /= 1024.0;

		return trim(inBytes, 2) + " T" + unit;
	}

	public static String trim(double d, int places) {
		String out = Double.toString(d);
		if (out.indexOf('.') != -1) {
			return out.substring(0, Math.min(out.indexOf('.') + places, out.length()));
		}
		return out;
	}

	public static String truncate(String str, int max, boolean trimBack) {
		if (str.length() < max) {
			return str;
		} else {
			if (trimBack)
				return str.substring(0, max - 3) + "...";
			else
				// trim from front
				return "..." + str.substring(str.length() - (max - 3), str.length());
		}
	}

	public static String formatDateAppleLike(Date date, boolean useAgo) {

		if (date == null) {
			return "never";
		}
		boolean inTheFuture = false;
		int secAgo = (int) (((new Date()).getTime() - date.getTime()) / 1000);

		if (secAgo < 0) {
			inTheFuture = true;
			secAgo = -secAgo;
		}
		int minAgo = secAgo / 60;
		int hoursAgo = minAgo / 60;
		int daysAgo = hoursAgo / 24;
		int monthsAgo = daysAgo / 31;
		String ret = "";
		if (secAgo < 5) {
			return "now";
		} else if (secAgo < 60) {
			ret = "<1 minute";
		} else if (minAgo == 1) {
			ret = "1 minute";
		} else if (minAgo < 60) {
			ret = minAgo + " minutes";
		} else if (hoursAgo == 1) {
			ret = "1 hour";
		} else if (hoursAgo < 24) {
			ret = hoursAgo + " hours";
		} else if (daysAgo == 1) {
			if (inTheFuture) {
				return "tomorrow";
			} else {
				return "yesterday";
			}
		} else if (daysAgo < 62) {
			ret = daysAgo + " days";
		} else if (monthsAgo < 24) {
			ret = (monthsAgo) + " months";
		} else {
			return "Years";
		}

		if (useAgo) {
			if (inTheFuture) {
				return "in " + ret;
			} else {
				return ret + " ago";
			}
		} else {
			return ret;
		}
	}

	public static String formatDateAppleLike(Date lastDate) {
		return StringTools.formatDateAppleLike(lastDate, true);
	}

}
