package ru.redserver.coderemover;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
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
		int loggerConfigureTime = 0, readTime = 0, searchTime = 0, applyTime = 0, writeTime = 0;

		// Настраиваем Logger
		try {
			Formatter formatter = new Formatter() {
				Date date = new Date();
				SimpleDateFormat formatter = new SimpleDateFormat("H:mm:ss");

				@Override
				public String format(LogRecord record) {
					date.setTime(record.getMillis());
					StringBuilder sb = new StringBuilder();
					sb.append("[");
					sb.append(formatter.format(date));
					sb.append("] [");
					sb.append(record.getLevel().toString());
					sb.append("]: ");
					sb.append(formatMessage(record));
					sb.append("\n");
					return sb.toString();
				}
			};
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
			if(args.length < 2) throw new IllegalArgumentException("Too few arguments: <input file> <output file>");
			File inputFile = new File(args[0]);
			if(!inputFile.exists() || !inputFile.isFile()) throw new IllegalArgumentException("Input file does not exists: " + inputFile);

			File outputFile = new File(args[1]);

			LOG.log(Level.INFO, "Input file: {0}", inputFile.getAbsolutePath());
			LOG.log(Level.INFO, "Output file: {0}", outputFile.getAbsolutePath());

			LOG.log(Level.INFO, "Загрузка списка файлов...");
			// Загружаем Jar файл
			ClassCollection classCollection = JarManager.loadClassesFromJar(inputFile);
			LOG.log(Level.INFO, "Было загружено {0} Файлов, из них {1} классов.", new Object[]{classCollection.getClasses().size() + classCollection.getExtraFiles().size(), classCollection.getClasses().size()});

			readTime += timer.flip();

			LOG.log(Level.INFO, "Поиск аннотации Removable...");
			// Ищем аннотации в загруженных классах
			Map<CtClass, ClassChangeList> list = new HashMap<>();
			for(CtClass clazz : classCollection.getClasses()) {
				try {
					ClassChangeList classChangeList = AnnotationProccessor.processClass(clazz, false);
					// Проверяем, найдена ли аннотация в классе
					if(!classChangeList.isUnchanged()) {
						if(DEEP_LOG)
							LOG.log(Level.INFO, "В классе {0} была найдена аннотация Removable.", new Object[]{clazz.getName()});
						list.put(clazz, classChangeList);
					}
				} catch (ClassNotFoundException | NotFoundException | CannotCompileException ex) {
					LOG.log(Level.SEVERE, "Произошла ошибка при обработке класса.", ex);
				}
			}
			LOG.log(Level.INFO, "Поиск аннотаций Removable завершён. Было найдено {0} классов", new Object[]{list.size()});

			searchTime += timer.flip();

			LOG.log(Level.INFO, "Применение изменений в классах...");
			// Применяем изменения в классах
			for(Iterator<Map.Entry<CtClass, ClassChangeList>> it = list.entrySet().iterator(); it.hasNext();) {
				Map.Entry<CtClass, ClassChangeList> entry = it.next();
				if(entry.getValue().isRemoveClass()) {
					// Удаляем класс
					classCollection.getClasses().removeIf(clazz -> clazz.getName().equals(entry.getKey().getName()));
					it.remove();
					LOG.log(Level.INFO, "Класс {0} был удалён.", new Object[]{entry.getKey().getName()});
				} else {
					// Удаляем методы и поля
					if(DEEP_LOG)
						LOG.log(Level.INFO, "Применяю изменения для класса {0}.", new Object[]{entry.getKey().getName()});
					try {
						AnnotationProccessor.applyChange(entry.getValue(), entry.getKey());
					} catch (CannotCompileException ex) {
						LOG.log(Level.SEVERE, "Произошла ошибка при применении изменений в классе.", ex);
					}
				}
			}

			applyTime += timer.flip();

			try {
				// Записываем новый jar
				Files.deleteIfExists(outputFile.toPath());
				JarManager.writeClasssesToJar(outputFile, classCollection);
			} catch (CannotCompileException ex) {
				LOG.log(Level.SEVERE, "Произошла ошибка записи файлов.", ex);
			}

			writeTime += timer.flip();

			if(DEEP_LOG)
				LOG.log(Level.INFO, "Code Remover завершил работу за {0}ms (loggerConfigure {1}ms, read {2}ms, search {3}ms, apply {4}ms, write {5}ms).", new Object[]{loggerConfigureTime + readTime + searchTime + applyTime + writeTime, loggerConfigureTime, readTime, searchTime, applyTime, writeTime});
			else
				LOG.log(Level.INFO, "Code Remover завершил работу за {0}ms.", new Object[]{loggerConfigureTime + readTime + searchTime + applyTime + writeTime});
		} catch (IOException | IllegalArgumentException ex) {
			System.out.println(ex.getMessage());
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
