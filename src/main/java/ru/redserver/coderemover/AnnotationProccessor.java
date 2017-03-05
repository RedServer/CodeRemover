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
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

/**
 * Обрабатывает аннотации
 * @author Nuclear
 */
public final class AnnotationProccessor {

	static final String DATA_SEPARATOR = "<::>";

	private final Set<String> deletedIfaces = new HashSet<>(); // удалённые интерфейсы
	private final Set<String> deletedFields = new HashSet<>(); // удалённые поля
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
	public CtClass processClass(CtClass clazz) throws Exception {
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
				CodeRemover.LOG.info("Removed " + (isInterface ? "interface" : "class") + ": " + clazz.getName());
				return null;
			} else {
				// Удаляем аннотацию
				AnnotationsAttribute attr = (AnnotationsAttribute)clazz.getClassFile().getAttribute(AnnotationsAttribute.invisibleTag);
				attr.removeAnnotation(Removable.class.getName());
				clazz.getClassFile().addAttribute(attr);
				CodeRemover.LOG.info(String.format("Removed annotation @%s from: %s", Removable.class.getSimpleName(), clazz.getName()));
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
				CodeRemover.LOG.info(String.format("Removed interface usage %s in %s", iFaceName, clazz.getName()));
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
			CodeRemover.LOG.info(String.format("Changed superclass for %s: %s -> %s", clazz.getName(), oldSuper, superName));
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
				if(CodeRemover.DEEP_LOG) CodeRemover.LOG.log(Level.WARNING, "Unknown class", ex);
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
					deletedFields.add(field.getName() + DATA_SEPARATOR + field.getSignature());
				} else {
					// Удаляем аннотацию
					AnnotationsAttribute attr = (AnnotationsAttribute)field.getFieldInfo().getAttribute(AnnotationsAttribute.invisibleTag);
					attr.removeAnnotation(Removable.class.getName());
					field.getFieldInfo().addAttribute(attr);
					CodeRemover.LOG.info(String.format("Removed annotation @%s from field: %s.%s", Removable.class.getSimpleName(), clazz.getName(), field.getName()));
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
					CodeRemover.LOG.info("Removed method: " + clazz.getName() + "." + method.getName() + method.getSignature());
				} else {
					// Удаляем аннотацию
					AnnotationsAttribute attr = (AnnotationsAttribute)method.getMethodInfo().getAttribute(AnnotationsAttribute.invisibleTag);
					attr.removeAnnotation(Removable.class.getName());
					method.getMethodInfo().addAttribute(attr);
					CodeRemover.LOG.info(String.format("Removed annotation @%s from method: %s.%s", Removable.class.getSimpleName(), clazz.getName(), method.getName() + method.getSignature()));
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
		// static конструктор
		CtConstructor staticConstructor = clazz.getClassInitializer();
		if(staticConstructor != null) {
			editor.setConstructor(staticConstructor);
			staticConstructor.instrument(editor);
		}

		// А теперь можно удалить сами поля (если это сделать раньше, можно получить NotFound на этапе чистки конструкторов)
		for(String field : deletedFields) {
			String[] fieldData = field.split(DATA_SEPARATOR, 2);
			clazz.removeField(clazz.getDeclaredField(fieldData[0], fieldData[1]));
			rebuild = true;
			CodeRemover.LOG.info("Removed field: " + clazz.getName() + "." + fieldData[0]);
		}

		deletedFields.clear(); // очищаем для следующего класса
	}

	// TODO: Пока плохо выполняет свою работу, поэтому лучше не использовать. Требуется доработка.
	@Deprecated
	private void checkConstructor(CtConstructor constructor) throws BadBytecode {
		MethodInfo minfo = constructor.getMethodInfo();
		CodeAttribute code = minfo.getCodeAttribute();
		if(code == null) return;
		ConstPool cpool = minfo.getConstPool();
		CodeIterator it = code.iterator();

		int lastAload0 = -1; // позиция последней инструкции aload_0
		while(it.hasNext()) {
			int pos = it.next();
			int opcode = it.byteAt(pos);
			if(opcode == Opcode.ALOAD_0) {
				lastAload0 = pos;
			} else if(opcode == Opcode.PUTFIELD || opcode == Opcode.PUTSTATIC) {
				int fieldIndex = it.u16bitAt(pos + 1);
				String fieldClass = cpool.getFieldrefClassName(fieldIndex);
				String fieldName = cpool.getFieldrefName(fieldIndex);
				String fieldSign = cpool.getFieldrefType(fieldIndex);
				if(fieldClass.equals(constructor.getDeclaringClass().getName()) && deletedFields.contains(fieldName + DATA_SEPARATOR + fieldSign)) {
					for(int pos2 = lastAload0; pos2 <= (pos + 2); pos2++) {
						it.writeByte(Opcode.NOP, pos2);
					}
					CodeRemover.LOG.info(String.format("Removed access to deleted field '%s' in %s.%s%s",
							fieldName, constructor.getDeclaringClass().getName(),
							constructor.isClassInitializer() ? MethodInfo.nameClinit : MethodInfo.nameInit,
							constructor.getSignature()
					));
				}
			}
		}
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
			if(CodeRemover.DEEP_LOG) CodeRemover.LOG.log(Level.WARNING, "Unknown interface", ex);
			return false;
		}
	}

}
