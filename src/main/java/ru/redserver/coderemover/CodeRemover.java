package ru.redserver.coderemover;

import ru.redserver.coderemover.io.JarContents;
import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import ru.redserver.coderemover.io.JarManager;

/**
 * Удаляет методы, поля, классы, помеченные аннотацией Removable
 * @author Andrey, Nuclear
 */
public final class CodeRemover {

	public static final String VERSION = "1.5";
	public static final Logger LOG = Logger.getLogger("CodeRemover");
	public static final boolean DEEP_LOG = false; // Включает более подробные логи

	public void run(String args[]) {
		Timer timer = new Timer();
		int readTime = 0, applyTime = 0, writeTime = 0;

		// Настраиваем Logger
		try {
			LogFormatter formatter = new LogFormatter();
			LOG.setUseParentHandlers(false);

			if(DEEP_LOG) {
				FileHandler fileHandler = new FileHandler("coderemover.log", true);
				fileHandler.setFormatter(formatter);
				LOG.addHandler(fileHandler);
			}
			LOG.addHandler(new LogHandler());
		} catch (IOException e) {
			System.err.println("Logger not configured");
		}

		// Запуск программы
		try {
			// Проверяем входные данные
			if(args.length < 2) throw new IllegalArgumentException("Too small arguments: <input file> <output file>");
			File inputFile = new File(args[0]);
			if(!inputFile.exists() || !inputFile.isFile()) throw new IllegalArgumentException("File doesn't exists: " + inputFile);

			File outputFile = new File(args[1]);

			if(DEEP_LOG) {
				LOG.log(Level.INFO, "Input file: {0}", inputFile.getAbsolutePath());
				LOG.log(Level.INFO, "Output file: {0}", outputFile.getAbsolutePath());
			}

			// Загружаем Jar файл
			JarContents contents = JarManager.loadClassesFromJar(inputFile);
			LOG.log(Level.INFO, "Loaded {0} classes and {1} resources.", new Object[]{contents.classes.size(), contents.resources.size()});

			readTime += timer.flip();

			AnnotationProccessor processor = new AnnotationProccessor();
			processor.removeClasses(contents);
			processor.processClasses(contents);

			applyTime += timer.flip();
			JarManager.writeClasssesToJar(outputFile, contents);
			writeTime += timer.flip();

			if(DEEP_LOG) {
				LOG.log(Level.INFO, "Code Remover завершил работу за {0}ms (read {1}ms, apply {2}ms, write {3}ms).", new Object[]{readTime + applyTime + writeTime, readTime, applyTime, writeTime});
			} else {
				LOG.log(Level.INFO, "Task done in {0}ms.", new Object[]{readTime + applyTime + writeTime});
			}
		} catch (Exception ex) {
			LOG.log(Level.SEVERE, "Error occurred", ex);
			System.exit(1);
		}
	}

	private static class Timer {

		private long start;

		public Timer() {
			start = System.currentTimeMillis();
		}

		public int flip() {
			int rv = (int)(System.currentTimeMillis() - start);
			start = System.currentTimeMillis();
			return rv;
		}

	}

}
