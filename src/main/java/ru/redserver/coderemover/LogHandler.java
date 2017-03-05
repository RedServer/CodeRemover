package ru.redserver.coderemover;

import java.util.logging.ConsoleHandler;

public final class LogHandler extends ConsoleHandler {

	public LogHandler() {
		setOutputStream(System.out);
		setFormatter(new LogFormatter());
	}

}
