package ru.redserver.coderemover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import javassist.CtClass;

/**
 * Содержит в себе всю необходимую информацию для обратной сборки Jar файла
 * @author Nuclear
 */
public final class ClassCollection {

	private final Map<String, byte[]> extraFiles = new HashMap<>();
	private Manifest manifest;
	private final List<CtClass> classes = new ArrayList<>();

	/**
	 * Получить список файлов (Все файлы кроме .class)
	 * @return Список файлов
	 */
	public Map<String, byte[]> getExtraFiles() {
		return extraFiles;
	}

	/**
	 * Установить Manifest Jar файла
	 * @param manifest Manifest
	 */
	public void setManifest(Manifest manifest) {
		this.manifest = manifest;
	}

	/**
	 * Получить Manifest Jar файла
	 * @return Manifest
	 */
	public Manifest getManifest() {
		return manifest;
	}

	/**
	 * Получения списка классов Jar файла
	 * @return Список классов
	 */
	public List<CtClass> getClasses() {
		return classes;
	}

}
