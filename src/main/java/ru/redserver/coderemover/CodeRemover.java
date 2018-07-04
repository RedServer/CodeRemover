package ru.redserver.coderemover;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import ru.redserver.coderemover.io.JarContents;
import java.util.logging.Level;
import ru.redserver.coderemover.io.JarManager;

/**
 * Удаляет методы, поля, классы, помеченные аннотацией Removable
 * @author Andrey, Nuclear
 */
public final class CodeRemover {

	public static final String VERSION = "1.7";
	public static final boolean DEBUG_MODE = false; // Включает более подробные логи

	public void run(String args[]) {
		Timer timer = new Timer();
		int readTime = 0, applyTime = 0, writeTime = 0;
		List<String> argsList = Arrays.asList(args);

		// Запуск программы
		try {
			// Проверяем входные данные
			if(argsList.size() < 2) throw new IllegalArgumentException("Too small arguments: <input file> <output file>");
			Path inputFile = Paths.get(argsList.get(0));
			Path outputFile = Paths.get(argsList.get(1));
			boolean removeOnly = argsList.contains("--remove-only"); // Режим удаления классов без применения исправлений
			if(!Files.isRegularFile(inputFile)) throw new IllegalArgumentException("File doesn't exists: " + inputFile);

			if(DEBUG_MODE) {
				SimpleLogger.instance.log(Level.INFO, "Input file: {0}", inputFile.toAbsolutePath());
				SimpleLogger.instance.log(Level.INFO, "Output file: {0}", outputFile.toAbsolutePath());
			}

			// Загружаем Jar файл
			JarContents contents = JarManager.loadClassesFromJar(inputFile);
			SimpleLogger.instance.log(Level.INFO, "Loaded {0} classes and {1} resources.", new Object[]{contents.classes.size(), contents.resources.size()});
			if(removeOnly) SimpleLogger.instance.info("Unsing remove only mode.");

			readTime += timer.flip();

			AnnotationProccessor processor = new AnnotationProccessor();
			processor.removeClasses(contents);
			processor.processClasses(contents, !removeOnly);

			applyTime += timer.flip();
			JarManager.writeClasssesToJar(outputFile, contents);
			writeTime += timer.flip();

			if(DEBUG_MODE) {
				SimpleLogger.instance.log(Level.INFO, "Code Remover завершил работу за {0}ms (read {1}ms, apply {2}ms, write {3}ms).", new Object[]{readTime + applyTime + writeTime, readTime, applyTime, writeTime});
			} else {
				SimpleLogger.instance.log(Level.INFO, "Task done in {0}ms.", new Object[]{readTime + applyTime + writeTime});
			}
		} catch (Exception ex) {
			SimpleLogger.instance.log(Level.SEVERE, "An error occurred", ex);
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
