package ru.redserver.coderemover;

import java.util.logging.Level;
import java.util.regex.Pattern;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.MethodInfo;

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

	private static void printBytecode(MethodInfo minfo, StringBuilder sb) {
		ConstPool pool = minfo.getConstPool();
		CodeAttribute code = minfo.getCodeAttribute();
		if(code == null) return;

		CodeIterator iterator = code.iterator();
		try {
			while(iterator.hasNext()) {
				int pos = iterator.next();
				sb.append(pos).append(": ").append(InstructionPrinter.instructionString(iterator, pos, pool)).append("\n");
			}
		} catch (BadBytecode e) {
			CodeRemover.LOG.log(Level.SEVERE, "Ошибка чтения байт-кода", e);
		}
		CodeRemover.LOG.info(sb.toString().trim());
	}

	/**
	 * Выводит простой байт-код тела метода
	 * @param constr Конструктор
	 */
	public static void printBytecode(CtConstructor constr) {
		StringBuilder sb = new StringBuilder();
		sb.append("Bytecode of constructor: ")
				.append(constr.getDeclaringClass().getName())
				.append(".")
				.append(constr.isClassInitializer() ? MethodInfo.nameClinit : MethodInfo.nameInit)
				.append(constr.getSignature())
				.append("\n");
		printBytecode(constr.getMethodInfo2(), sb);
	}

	/**
	 * Выводит простой байт-код тела метода
	 * @param method Метод
	 */
	public static void printBytecode(CtMethod method) {
		StringBuilder sb = new StringBuilder();
		sb.append("Bytecode of method: ")
				.append(method.getDeclaringClass().getName())
				.append(".")
				.append(method.getName())
				.append(method.getSignature())
				.append("\n");
		printBytecode(method.getMethodInfo2(), sb);
	}

}
