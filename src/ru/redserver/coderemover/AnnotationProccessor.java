package ru.redserver.coderemover;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;

/**
 * Обрабатывает аннотации
 * @author Nuclear
 */
public final class AnnotationProccessor {

	private final Set<String> deletedIfaces = new HashSet<>(); // удалённые интерфейсы
	private final ClassPool pool;

	public AnnotationProccessor(ClassPool pool) {
		this.pool = pool;
	}

	/**
	 * Обрабатывает аннотацию Removable в переданном классе
	 * @param clazz Класс
	 * @return Возвращает обработанный класс. Если класс был удалён, то возвращает null
	 * @throws ClassNotFoundException
	 * @throws NotFoundException
	 * @throws CannotCompileException
	 */
	public CtClass processClass(CtClass clazz) throws ClassNotFoundException, NotFoundException, CannotCompileException {
		// Проверяем класс
		Removable classAnnotation = (Removable)clazz.getAnnotation(Removable.class);
		if(classAnnotation != null) {
			if(classAnnotation.remove()) {
				boolean isInterface = (clazz.isInterface() && !clazz.isAnnotation());
				CodeRemover.LOG.info("Удалён " + (isInterface ? "интерфейс" : "класс") + ": " + clazz.getName());
				if(isInterface) deletedIfaces.add(clazz.getName());
				return null;
			} else {
				// Удаляем аннотацию
				AnnotationsAttribute attr = (AnnotationsAttribute)clazz.getClassFile().getAttribute(AnnotationsAttribute.invisibleTag);
				attr.removeAnnotation(Removable.class.getName());
				clazz.getClassFile().addAttribute(attr);
				CodeRemover.LOG.info(String.format("Удалена аннотация @%s для класса: %s", Removable.class.getSimpleName(), clazz.getName()));
			}
		}

		checkInterfaces(clazz);

		// Проверяем методы
		for(CtMethod method : clazz.getDeclaredMethods()) {
			Removable methodAnnotation = (Removable)method.getAnnotation(Removable.class);
			if(methodAnnotation != null) {
				if(methodAnnotation.remove()) {
					// Удаляем метод
					clazz.removeMethod(method);
					CodeRemover.LOG.info("Удалён метод: " + clazz.getName() + "." + method.getName() + method.getSignature());
				} else {
					// Удаляем аннотацию
					AnnotationsAttribute attr = (AnnotationsAttribute)method.getMethodInfo().getAttribute(AnnotationsAttribute.invisibleTag);
					attr.removeAnnotation(Removable.class.getName());
					method.getMethodInfo().addAttribute(attr);
					CodeRemover.LOG.info(String.format("Удалена аннотация @%s для метода: %s.%s", Removable.class.getSimpleName(), clazz.getName(), method.getName() + method.getSignature()));
				}
			}
		}

		// Проверяем поля
		List<CtField> removeFields = new ArrayList<>();

		for(CtField field : clazz.getDeclaredFields()) {
			Removable fieldAnnotation = (Removable)field.getAnnotation(Removable.class);
			if(fieldAnnotation != null) {
				if(fieldAnnotation.remove()) {
					removeFields.add(field);
				} else {
					// Удаляем аннотацию
					AnnotationsAttribute attr = (AnnotationsAttribute)field.getFieldInfo().getAttribute(AnnotationsAttribute.invisibleTag);
					attr.removeAnnotation(Removable.class.getName());
					field.getFieldInfo().addAttribute(attr);
					CodeRemover.LOG.info(String.format("Удалена аннотация @%s для поля: %s.%s", Removable.class.getSimpleName(), clazz.getName(), field.getName()));
				}
			}
		}

		// Убираем инициализацию удалённых полей из конструкторов, чтобы не получить NoSuchFieldError
		if(!removeFields.isEmpty()) {
			ConstructorCleaner editor = new ConstructorCleaner(clazz, removeFields);
			for(CtConstructor constructor : clazz.getDeclaredConstructors()) {
				editor.setConstructor(constructor);
				constructor.instrument(editor);
			}

			// А теперь можно удалить сами поля (если это сделать раньше, можно получить NotFound на этапе чистки конструкторов)
			for(CtField field : removeFields) {
				clazz.removeField(field);
				CodeRemover.LOG.info("Удалено поле: " + clazz.getName() + "." + field.getName());
			}
		}

		return clazz;
	}

	/**
	 * Проверяет интерфейсы класса и убирает те, которые были удалены
	 * @param clazz Класс
	 */
	private void checkInterfaces(CtClass clazz) throws ClassNotFoundException, NotFoundException {
		String[] ifaces = clazz.getClassFile2().getInterfaces(); // Получаем названия классов интерфейсов (так нам не потребуются зависимые библиотеки)
		if(ifaces.length == 0) return;
		List<String> list = new ArrayList<>(); // оставшиеся интерфейсы

		boolean isDirty = false; // флаг изменений
		for(String iFaceName : ifaces) {
			if(mayDeleteInterface(iFaceName)) {
				isDirty = true;
				CodeRemover.LOG.info(String.format("Удалено использование интерфейса %s в %s", iFaceName, clazz.getName()));
			} else {
				list.add(iFaceName);
			}
		}
		if(isDirty) clazz.getClassFile2().setInterfaces(list.toArray(new String[list.size()]));
	}

	/**
	 * Проверяет, нужно ли удалять интерфейс
	 * @param className Полное имя клсасаа интерфейса
	 * @return Результат
	 */
	private boolean mayDeleteInterface(String className) throws ClassNotFoundException {
		if(deletedIfaces.contains(className)) return true;
		try {
			CtClass clazz = pool.get(className);
			Removable removable = (Removable)clazz.getAnnotation(Removable.class);
			return (removable != null && removable.remove());
		} catch (NotFoundException ex) { // Не проверяем классы во внешних библиотеках
			if(CodeRemover.DEEP_LOG) CodeRemover.LOG.log(Level.WARNING, "Неизвестный интерфейс", ex);
			return false;
		}
	}

}
