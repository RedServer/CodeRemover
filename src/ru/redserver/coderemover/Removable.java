package ru.redserver.coderemover;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Removable {

	/**
	 * Будет ли удалён элемент или нет
	 * @return
	 */
	boolean remove();

}
