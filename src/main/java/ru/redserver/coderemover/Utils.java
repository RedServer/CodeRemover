package ru.redserver.coderemover;

import java.util.regex.Pattern;

public final class Utils {

	private static final Pattern PATTERN_PART_OF_CLASS = Pattern.compile("\\$[0-9]+$", Pattern.CASE_INSENSITIVE);
	private static final String PACKAGE_SEPARATOR = "/";

	private Utils() {
	}

	/**
	 * Проверяет, является ли класс составной частью класса (Example$1)
	 * @param name Имя класса, включая пакет
	 * @return true, если это часть класса
	 */
	public static boolean isPartOfClass(String name) {
		return PATTERN_PART_OF_CLASS.matcher(name).matches();
	}

	/**
	 * Заменяет разделители пакетов на привычные точки
	 * @param name Полное внутреннее имя класса
	 * @return
	 */
	public static String normalizeName(String name) {
		if(name == null) return null;
		return name.replace(PACKAGE_SEPARATOR, ".");
	}

	/**
	 * Получить имя родительского класса, если это подкласс или часть. Каждый вызов позволяет подняться на ступень выше.
	 * @param name Имя класса, включая пакет
	 * @return Имя родительского класса. null если это класс верхнего уровня.
	 */
	public static String getParentClassName(String name) {
		// Отделяем пакет от имени класса
		int pointPos = name.lastIndexOf(PACKAGE_SEPARATOR);
		String packageName = null;
		String className = name;
		if(pointPos >= 0) {
			packageName = name.substring(0, pointPos);
			className = name.substring(pointPos + 1);
		}

		int sepPos = className.lastIndexOf("$");
		if(sepPos < 0) return null; // это класс верхнего уровня
		String parentName = className.substring(0, sepPos);
		if(packageName != null) parentName = packageName + PACKAGE_SEPARATOR + parentName;
		return parentName;
	}

}
