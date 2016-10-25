package ru.redserver.coderemover;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import static ru.redserver.coderemover.CodeRemover.LOG;

/**
 * Ищет аннотации и создаёт список изменений в классе, а так же применяет их
 * @author Nuclear
 */
public class AnnotationProccessor {

	public static ClassChangeList processClass(CtClass clazz, boolean parent) throws ClassNotFoundException, NotFoundException, CannotCompileException {
		ClassChangeList classChangeList = new ClassChangeList();

		// Проверяем класс
		Removable classAnnotation = (Removable)clazz.getAnnotation(Removable.class);
		if(classAnnotation != null) {
			if(classAnnotation.remove()) {
				// Помечаем класс на удаление, сразу возвращаем список изменений класса
				classChangeList.removeClass();
				return classChangeList;
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
					// Помечаем метод на удаление
					classChangeList.getMethods().add(method.getName());
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
					// Помечаем поле на удаление
					classChangeList.getFields().add(field.getName());
				} else {
					// Удаляем аннотацию
					AnnotationsAttribute attr = (AnnotationsAttribute)field.getFieldInfo().getAttribute(AnnotationsAttribute.invisibleTag);
					attr.removeAnnotation(Removable.class.getName());
					field.getFieldInfo().addAttribute(attr);
					LOG.info(String.format("Удалена аннотация @%s для поля: %s.%s", Removable.class.getSimpleName(), clazz.getName(), field.getName()));
				}
			}
		}

		return classChangeList;
	}

	public static CtClass applyChange(ClassChangeList changeList, CtClass clazz) throws CannotCompileException {
		// Удаляю методы из класса
		changeList.getMethods().forEach(methodName -> {
			try {
				CtMethod method = clazz.getDeclaredMethod(methodName);
				clazz.removeMethod(method);
				LOG.info("Удалён метод: " + clazz.getName() + "." + method.getName() + method.getSignature());
			} catch (NotFoundException ex) {
			}
		});
		// Удаляю поля из класса
		changeList.getFields().forEach(fieldName -> {
			try {
				clazz.removeField(clazz.getDeclaredField(fieldName));
				LOG.info("Удалено поле " + clazz.getName() + "." + fieldName);
			} catch (NotFoundException ex) {
			}
		});

		return clazz;
	}

}
