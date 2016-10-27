package ru.redserver.coderemover;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.ConsoleHandler;
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

	public static final ClassPool CLASS_POOL = new ClassPool(true);
	public static final Logger LOG = Logger.getLogger("CodeRemover");

	// Включает более подробные логи
	public static final boolean DEEP_LOG = false;

	public void run(String args[]) {
		Timer timer = new Timer();
		int loggerConfigureTime = 0, readTime = 0, applyTime = 0, writeTime = 0;

		// Настраиваем Logger
		try {
			LogFormatter formatter = new LogFormatter();
			LOG.setUseParentHandlers(false);

			ConsoleHandler consoleHandler = new ConsoleHandler();
			consoleHandler.setFormatter(formatter);

			if(DEEP_LOG) {
				FileHandler fileHandler = new FileHandler("coderemover.log", true);
				fileHandler.setFormatter(formatter);
				LOG.addHandler(fileHandler);
			}
			LOG.addHandler(consoleHandler);
		} catch (IOException e) {
			System.err.println("Logger not configured");
		}
		loggerConfigureTime += timer.flip();

		// Запуск программы
		try {
			LOG.log(Level.INFO, "------ Code Remover ------");

			// Проверяем входные данные
			if(args.length < 2) throw new IllegalArgumentException("Слишком мало аргументов: <input file> <output file>");
			File inputFile = new File(args[0]);
			if(!inputFile.exists() || !inputFile.isFile()) throw new IllegalArgumentException("Файл не существует: " + inputFile);

			File outputFile = new File(args[1]);

			if(DEEP_LOG) {
				LOG.log(Level.INFO, "Input file: {0}", inputFile.getAbsolutePath());
				LOG.log(Level.INFO, "Output file: {0}", outputFile.getAbsolutePath());
			}

			if(DEEP_LOG) LOG.log(Level.INFO, "Загрузка списка файлов...");
			// Загружаем Jar файл
			ClassCollection classCollection = JarManager.loadClassesFromJar(inputFile);
			if(DEEP_LOG) LOG.log(Level.INFO, "Было загружено {0} Файлов, из них {1} классов.", new Object[]{classCollection.getClasses().size() + classCollection.getExtraFiles().size(), classCollection.getClasses().size()});

			readTime += timer.flip();

			if(DEEP_LOG) LOG.log(Level.INFO, "Поиск аннотации Removable...");
			AnnotationProccessor processor = new AnnotationProccessor(CodeRemover.CLASS_POOL);

			// Ищем аннотации в загруженных классах
			for(Iterator<CtClass> it = classCollection.getClasses().iterator(); it.hasNext();) {
				CtClass clazz = it.next();
				try {
					if(processor.processClass(clazz) == null) it.remove();
				} catch (ClassNotFoundException | NotFoundException | CannotCompileException ex) {
					LOG.log(Level.SEVERE, "Произошла ошибка при обработке класса: " + clazz.getName(), ex);
				}
			}

			applyTime += timer.flip();

			try {
				// Записываем новый jar
				JarManager.writeClasssesToJar(outputFile, classCollection);
			} catch (CannotCompileException ex) {
				LOG.log(Level.SEVERE, "Произошла ошибка записи файлов", ex);
			}

			writeTime += timer.flip();

			if(DEEP_LOG)
				LOG.log(Level.INFO, "Code Remover завершил работу за {0}ms (loggerConfigure {1}ms, read {2}ms, apply {3}ms, write {4}ms).", new Object[]{loggerConfigureTime + readTime + applyTime + writeTime, loggerConfigureTime, readTime, applyTime, writeTime});
			else
				LOG.log(Level.INFO, "Code Remover завершил работу за {0}ms.", new Object[]{loggerConfigureTime + readTime + applyTime + writeTime});
		} catch (Exception ex) {
			LOG.log(Level.SEVERE, "Ошибка", ex);
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
