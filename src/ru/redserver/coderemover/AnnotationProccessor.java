package ru.redserver.coderemover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
	private final Set<CtField> deletedFields = new HashSet<>(); // удалённые поля
	private final Map<String, String> deletedClasses = new HashMap<>(); // удалённые классы (ключ - имя, значение - имя родителя)
	private final ClassPool pool;
	private boolean rebuild = false;

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
				if(isInterface) {
					deletedIfaces.add(clazz.getName());
				} else if(!clazz.isAnnotation() && !clazz.isEnum()) { // ненаследуемые
					deletedClasses.put(clazz.getName(), clazz.getClassFile2().getSuperclass());
				}
				CodeRemover.LOG.info("Удалён " + (isInterface ? "интерфейс" : "класс") + ": " + clazz.getName());
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
		checkSuperclass(clazz);
		checkFields(clazz);
		checkMethods(clazz);
		checkConstructors(clazz);

		if(rebuild) {
			clazz.rebuildClassFile();
			clazz.freeze();
			rebuild = false;
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
				rebuild = true;
				CodeRemover.LOG.info(String.format("Удалено использование интерфейса %s в %s", iFaceName, clazz.getName()));
			} else {
				list.add(iFaceName);
			}
		}
		if(isDirty) clazz.getClassFile2().setInterfaces(list.toArray(new String[list.size()]));
	}

	/**
	 * Проверяет, были ли удалены родительские классы и перенаправлят так, чтобы восстановить цепочку наследования
	 * @param clazz Класс
	 */
	private void checkSuperclass(CtClass clazz) throws ClassNotFoundException, CannotCompileException {
		String oldSuper = clazz.getClassFile2().getSuperclass();
		String superName = getSuperclass(oldSuper);
		if(!oldSuper.equals(superName)) {
			clazz.getClassFile2().setSuperclass(superName);
			clazz.replaceClassName(oldSuper, superName);
			rebuild = true;
			CodeRemover.LOG.info(String.format("Изменён родительский класс для %s: %s -> %s", clazz.getName(), oldSuper, superName));
		}
	}

	/**
	 * Ищет родительский класс
	 * @param className Старый родительский класс
	 * @return Новый родительский класс
	 */
	private String getSuperclass(String className) throws ClassNotFoundException {
		if(className.equals("java.lang.Object")) return className;

		String deletedSuper = deletedClasses.get(className);
		if(deletedSuper != null) {
			return getSuperclass(deletedSuper);
		} else {
			try {
				CtClass clazz = pool.get(className);
				Removable removable = (Removable)clazz.getAnnotation(Removable.class);
				if(removable != null && removable.remove()) {
					return getSuperclass(clazz.getClassFile2().getSuperclass());
				} else { // TODO TheAndrey: Не уверен в правильности логики здесь, но работает это пока как надо
					return clazz.getName();
				}
			} catch (NotFoundException ex) {
				if(CodeRemover.DEEP_LOG) CodeRemover.LOG.log(Level.WARNING, "Неизвестный класс", ex);
				return className;
			}
		}
	}

	/**
	 * Проверяет поля
	 * @param clazz Класс
	 */
	private void checkFields(CtClass clazz) throws ClassNotFoundException {
		for(CtField field : clazz.getDeclaredFields()) {
			Removable fieldAnnotation = (Removable)field.getAnnotation(Removable.class);
			if(fieldAnnotation != null) {
				if(fieldAnnotation.remove()) {
					deletedFields.add(field);
				} else {
					// Удаляем аннотацию
					AnnotationsAttribute attr = (AnnotationsAttribute)field.getFieldInfo().getAttribute(AnnotationsAttribute.invisibleTag);
					attr.removeAnnotation(Removable.class.getName());
					field.getFieldInfo().addAttribute(attr);
					CodeRemover.LOG.info(String.format("Удалена аннотация @%s для поля: %s.%s", Removable.class.getSimpleName(), clazz.getName(), field.getName()));
				}
			}
		}
	}

	/**
	 * Проверяет методы
	 * @param clazz Класс
	 */
	private void checkMethods(CtClass clazz) throws ClassNotFoundException, NotFoundException {
		for(CtMethod method : clazz.getDeclaredMethods()) {
			Removable methodAnnotation = (Removable)method.getAnnotation(Removable.class);
			if(methodAnnotation != null) {
				if(methodAnnotation.remove()) {
					// Удаляем метод
					clazz.removeMethod(method);
					rebuild = true;
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
	}

	/**
	 * Проверяет конструкторы
	 * @param clazz Класс
	 */
	private void checkConstructors(CtClass clazz) throws NotFoundException, CannotCompileException {
		// Убираем инициализацию удалённых полей из конструкторов, чтобы не получить NoSuchFieldError
		if(deletedFields.isEmpty()) return;
		ConstructorCleaner editor = new ConstructorCleaner(clazz, deletedFields);
		for(CtConstructor constructor : clazz.getDeclaredConstructors()) {
			editor.setConstructor(constructor);
			constructor.instrument(editor);
		}

		// А теперь можно удалить сами поля (если это сделать раньше, можно получить NotFound на этапе чистки конструкторов)
		for(CtField field : deletedFields) {
			clazz.removeField(field);
			rebuild = true;
			CodeRemover.LOG.info("Удалено поле: " + clazz.getName() + "." + field.getName());
		}

		deletedFields.clear(); // очищаем для следующего класса
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
