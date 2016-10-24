package ru.redserver.coderemover;

import java.util.HashSet;
import java.util.Set;

/**
 * Содержит в себе изменения в классе, т.е. какие методы или поля удалять, удалять ли сам класс
 * @author Nuclear
 */
public class ClassChangeList {

	private boolean removeClass = false;
	private final Set<String> methods = new HashSet<>();
	private final Set<String> fields = new HashSet<>();

	/**
	 * Пометить файл на удаление
	 */
	public void removeClass() {
		this.removeClass = true;
	}

	/**
	 * Удалён ли класс
	 * @return true если класс помечен на удаление
	 */
	public boolean isRemoveClass() {
		return removeClass;
	}

	/**
	 * Получить список удалённых полей
	 * @return Список удалённых полей
	 */
	public Set<String> getFields() {
		return fields;
	}

	/**
	 * Получить список удалённых методов
	 * @return Список удалённых методов
	 */
	public Set<String> getMethods() {
		return methods;
	}

	/**
	 * Проверяет, есть ли какие либо изменения в классе
	 * @return Возвращает true если изменений в классе нет
	 */
	public boolean isUnchanged() {
		return this.fields.isEmpty() && this.methods.isEmpty() && !removeClass;
	}

	/**
	 * Объеденяет списки изменений двух классов (родительского и дочернего)
	 * @param other Список изменений
	 */
	public void merge(ClassChangeList other) {
		if(!this.removeClass)
			this.removeClass = other.removeClass;
		this.fields.addAll(other.fields);
		this.methods.addAll(other.methods);
	}

}
