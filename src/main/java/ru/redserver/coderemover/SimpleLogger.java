package ru.redserver.coderemover;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class SimpleLogger {

	public static final Logger instance;

	private SimpleLogger() {
	}

	static {
		instance = Logger.getLogger("CodeRemoved");

		// Конфигурация Logger
		instance.setLevel(Level.ALL);
		instance.setUseParentHandlers(false);
		instance.addHandler(new ConsoleHandler());
	}

	private static class ConsoleHandler extends Handler {

		public ConsoleHandler() {
			setFormatter(new LoggerFormatter());
			setLevel(Level.FINER);
		}

		@Override
		public void publish(LogRecord record) {
			String msg = getFormatter().format(record);
			System.out.print(msg);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
		}

	}

	private static class LoggerFormatter extends Formatter {

		private final SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss");

		@Override
		public String format(LogRecord record) {
			StringBuilder builder = new StringBuilder();
			Throwable throwable = record.getThrown();

			builder.append(date.format(record.getMillis()));
			builder.append(" [");
			builder.append(record.getLevel().getLocalizedName().toUpperCase());
			builder.append("] ");
			builder.append(formatMessage(record));
			builder.append('\n');

			if(throwable != null) {
				StringWriter writer = new StringWriter();
				throwable.printStackTrace(new PrintWriter(writer));
				builder.append(writer);
			}

			return builder.toString();
		}

	}

}
