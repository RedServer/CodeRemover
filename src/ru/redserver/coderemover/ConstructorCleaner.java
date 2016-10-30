package ru.redserver.coderemover;

import java.util.Collection;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import static ru.redserver.coderemover.CodeRemover.LOG;

/**
 * Удаляет из конструкторов класса обращение к удалённым полям.
 * @author Andrey
 */
public final class ConstructorCleaner extends ExprEditor {

	private final CtClass clazz;
	private final Collection<String> removeFields;
	private CtConstructor constructor;

	/**
	 * @param clazz Класс
	 * @param removeFields Имена полей класса, которые будут удалены
	 */
	public ConstructorCleaner(CtClass clazz, Collection<String> removeFields) {
		this.clazz = clazz;
		this.removeFields = removeFields;
	}

	/**
	 * Установить текущий конструктор (используется для лога).
	 * @param constructor
	 */
	public void setConstructor(CtConstructor constructor) {
		this.constructor = constructor;
	}

	@Override
	public void edit(FieldAccess faccess) throws CannotCompileException {
		String fieldName = faccess.getFieldName(); // после удаления становится недоступно
		if(faccess.getClassName().equals(clazz.getName()) && removeFields.contains(fieldName)) {
			faccess.replace(""); // TODO: Чистит плоховато - после изменения, объект присваивается локальной переменной
			LOG.info(String.format("Удалено обращение к удалённому полю '%s' в: %s.<init>%s",
					fieldName,
					clazz.getName(),
					constructor != null ? constructor.getSignature() : "**NULL**"
			));
		}
	}

}
