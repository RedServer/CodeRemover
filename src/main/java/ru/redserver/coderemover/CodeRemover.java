package ru.redserver.coderemover;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import ru.redserver.coderemover.io.JarManager;

/**
 * Удаляет методы, поля, классы, помеченные аннотацией Removable
 * @author Andrey, Nuclear
 */
public class CodeRemover {

	public static final String VERSION = "1.5";
	public static final ClassPool CLASS_POOL = new ClassPool(true);
	public static final Logger LOG = Logger.getLogger("CodeRemover");
	public static final boolean DEEP_LOG = false; // Включает более подробные логи

	public void run(String args[]) {
		Timer timer = new Timer();
		int loggerConfigureTime = 0, readTime = 0, applyTime = 0, writeTime = 0;

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
		loggerConfigureTime += timer.flip();

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

			if(DEEP_LOG) LOG.log(Level.INFO, "Loading classes...");
			// Загружаем Jar файл
			ClassCollection classCollection = JarManager.loadClassesFromJar(inputFile);
			if(DEEP_LOG) LOG.log(Level.INFO, "Loaded {0} files total, and {1} classes.", new Object[]{classCollection.getClasses().size() + classCollection.getExtraFiles().size(), classCollection.getClasses().size()});

			readTime += timer.flip();

			if(DEEP_LOG) LOG.log(Level.INFO, "Searching for @Removable...");
			AnnotationProccessor processor = new AnnotationProccessor(CodeRemover.CLASS_POOL);

			// Ищем аннотации в загруженных классах
			HashSet<String> deletedClasses = new HashSet<>();

			for(Iterator<CtClass> it = classCollection.getClasses().iterator(); it.hasNext();) {
				CtClass clazz = it.next();
				try {
					if(processor.processClass(clazz) == null) {
						String name = clazz.getName();
						if(!Utils.isPartOfClass(name)) deletedClasses.add(name);
						it.remove();
					}
				} catch (ClassNotFoundException | NotFoundException | CannotCompileException ex) {
					LOG.log(Level.SEVERE, "Error occured while processing class: " + clazz.getName(), ex);
				}
			}

			// Удаляем вложенные классы и подклассы
			classCollection.getClasses().removeIf((CtClass clazz) -> {
				String name = clazz.getName();
				String parentName = name;
				while((parentName = Utils.getParentClassName(parentName)) != null) { // проверяем всех родителей, поднимаясь на уровень выше
					if(deletedClasses.contains(parentName)) {
						LOG.info("Removed subclass: " + name);
						return true;  // удаляем, если родитель удалён
					}
				}
				return false;
			});

			applyTime += timer.flip();

			try {
				// Записываем новый jar
				JarManager.writeClasssesToJar(outputFile, classCollection);
			} catch (CannotCompileException ex) {
				LOG.log(Level.SEVERE, "Error occured while writing classes", ex);
			}

			writeTime += timer.flip();

			if(DEEP_LOG)
				LOG.log(Level.INFO, "Code Remover завершил работу за {0}ms (loggerConfigure {1}ms, read {2}ms, apply {3}ms, write {4}ms).", new Object[]{loggerConfigureTime + readTime + applyTime + writeTime, loggerConfigureTime, readTime, applyTime, writeTime});
			else
				LOG.log(Level.INFO, "Task done in {0}ms.", new Object[]{loggerConfigureTime + readTime + applyTime + writeTime});
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
