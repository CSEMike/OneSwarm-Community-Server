package edu.washington.cs.oneswarm.community2.utils;

import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class ConciseLogFormatter extends Formatter {

	public String format(LogRecord record) {
		String [] nameToks = record.getLoggerName().split("\\.");
		String time = DateFormat.getInstance().format(new Date()); 
		
		return "[" + record.getLevel() + "] " + time + " " + nameToks[nameToks.length-1] + ": " + record.getMessage() + "\n";
	}

}
