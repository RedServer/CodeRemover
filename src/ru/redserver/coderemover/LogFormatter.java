package ru.redserver.coderemover;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Форматирование лога.
 */
public final class LogFormatter extends Formatter {

	private final Date date = new Date();
	private final SimpleDateFormat formatter = new SimpleDateFormat("H:mm:ss");

	@Override
	public String format(LogRecord record) {
		date.setTime(record.getMillis());
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		builder.append(formatter.format(date));
		builder.append("] [");
		builder.append(record.getLevel().toString());
		builder.append("]: ");
		builder.append(formatMessage(record));
		builder.append("\n");

		if(record.getThrown() != null) {
			StringWriter writer = new StringWriter();
			record.getThrown().printStackTrace(new PrintWriter(writer));
			builder.append(writer);
		}

		return builder.toString();
	}

}
