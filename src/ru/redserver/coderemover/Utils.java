package ru.redserver.coderemover;

import java.util.regex.Pattern;

public final class Utils {

	private static final Pattern PATTERN_PART_OF_CLASS = Pattern.compile("^[a-z0-9_\\.]+\\$[0-9]+$", Pattern.CASE_INSENSITIVE);

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
	 * Получить имя родительского класса, если это подкласс или часть. Каждый вызов позволяет подняться на ступень выше.
	 * @param name Имя класса, включая пакет
	 * @return Имя родительского класса. null если это класс верхнего уровня.
	 */
	public static String getParentClassName(String name) {
		// Отделяем пакет от имени класса
		int pointPos = name.lastIndexOf(".");
		String packageName = null;
		String className = name;
		if(pointPos >= 0) {
			packageName = name.substring(0, pointPos);
			className = name.substring(pointPos + 1);
		}

		int sepPos = className.lastIndexOf("$");
		if(sepPos < 0) return null; // это класс верхнего уровня
		String parentName = className.substring(0, sepPos);
		if(packageName != null) parentName = packageName + "." + parentName;
		return parentName;
	}

}
