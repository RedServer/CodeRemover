package ru.redserver.coderemover.io;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Manifest;
import org.objectweb.asm.tree.ClassNode;

/**
 * Содержит в себе всю необходимую информацию для обратной сборки Jar файла
 * @author Nuclear
 */
public final class JarContents {

	/**
	 * Список файлов (Все файлы кроме .class)
	 */
	public final Map<String, byte[]> resources = new LinkedHashMap<>();

	/**
	 * Список классов Jar файла
	 */
	public final Map<String, ClassNode> classes = new LinkedHashMap<>();

	/**
	 * Manifest Jar файла
	 */
	public Manifest manifest;

}
