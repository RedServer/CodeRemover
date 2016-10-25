package ru.redserver.coderemover;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import static ru.redserver.coderemover.CodeRemover.LOG;

/**
 * Обрабатывает аннотации
 * @author Nuclear
 */
public class AnnotationProccessor {

	/**
	 * Обрабатывает аннотацию Removable в переданном классе
	 * @param clazz Класс
	 * @return Возвращает обработанный класс. Если класс был удалён, то возвращает null
	 * @throws ClassNotFoundException
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	public static CtClass processClass(CtClass clazz) throws ClassNotFoundException, NotFoundException, CannotCompileException {
		// Проверяем класс
		Removable classAnnotation = (Removable)clazz.getAnnotation(Removable.class);
		if(classAnnotation != null) {
			if(classAnnotation.remove()) {
				return null;
			} else {
				// Удаляем аннотацию
				AnnotationsAttribute attr = (AnnotationsAttribute)clazz.getClassFile().getAttribute(AnnotationsAttribute.invisibleTag);
				attr.removeAnnotation(Removable.class.getName());
				clazz.getClassFile().addAttribute(attr);
				LOG.info(String.format("Удалена аннотация @%s для класса: %s", Removable.class.getSimpleName(), clazz.getName()));
			}
		}

		// Проверяем методы
		for(CtMethod method : clazz.getDeclaredMethods()) {
			Removable methodAnnotation = (Removable)method.getAnnotation(Removable.class);
			if(methodAnnotation != null) {
				if(methodAnnotation.remove()) {
					// Удаляем метод
					clazz.removeMethod(method);
					LOG.info("Удалён метод: " + clazz.getName() + "." + method.getName() + method.getSignature());
				} else {
					// Удаляем аннотацию
					AnnotationsAttribute attr = (AnnotationsAttribute)method.getMethodInfo().getAttribute(AnnotationsAttribute.invisibleTag);
					attr.removeAnnotation(Removable.class.getName());
					method.getMethodInfo().addAttribute(attr);
					LOG.info(String.format("Удалена аннотация @%s для метода: %s.%s", Removable.class.getSimpleName(), clazz.getName(), method.getName() + method.getSignature()));
				}
			}
		}

		// Проверяем поля
		for(CtField field : clazz.getDeclaredFields()) {
			Removable fieldAnnotation = (Removable)field.getAnnotation(Removable.class);
			if(fieldAnnotation != null) {
				if(fieldAnnotation.remove()) {
					// Удаляем поле
					clazz.removeField(field);
					LOG.info("Удалено поле " + clazz.getName() + "." + field.getName());
				} else {
					// Удаляем аннотацию
					AnnotationsAttribute attr = (AnnotationsAttribute)field.getFieldInfo().getAttribute(AnnotationsAttribute.invisibleTag);
					attr.removeAnnotation(Removable.class.getName());
					field.getFieldInfo().addAttribute(attr);
					LOG.info(String.format("Удалена аннотация @%s для поля: %s.%s", Removable.class.getSimpleName(), clazz.getName(), field.getName()));
				}
			}
		}

		return clazz;
	}

}
