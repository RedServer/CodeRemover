package ru.redserver.coderemover;

import java.util.Collection;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import static ru.redserver.coderemover.CodeRemover.LOG;

/**
 * Удаляет из конструкторов класса обращение к удалённым полям.
 * @author Andrey
 */
public final class ConstructorCleaner extends ExprEditor {

	private final CtClass clazz;
	private final Collection<CtField> removeFields;
	private CtConstructor constructor;

	/**
	 * @param clazz Класс
	 * @param removeFields Список полей класса, которые будут удалены
	 */
	public ConstructorCleaner(CtClass clazz, Collection<CtField> removeFields) {
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
		try {
			CtField field = faccess.getField();
			if(removeFields.contains(faccess.getField())) {
				faccess.replace(""); // TODO: Чистит плоховато - после изменения, объект присваивается локальной переменной
				LOG.info(String.format("Удалено обращение к удалённому полю '%s' в: %s.<init>%s",
						field.getName(),
						clazz.getName(),
						constructor != null ? constructor.getSignature() : "**NULL**"
				));
			}
		} catch (NotFoundException ex) {
			throw new RuntimeException(ex);
		}
	}

}
