package ru.redserver.coderemover;

import java.lang.reflect.Modifier;
import ru.redserver.coderemover.io.JarContents;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Обрабатывает аннотации
 * @author Nuclear
 */
public final class AnnotationProccessor {

	static final String DATA_SEPARATOR = "<::>";
	static final String OBJECT_CONSTRUCTOR = "<init>";
	static final String STATIC_CONSTRUCTOR = "<clinit>";
	static final String REMOVABLE_DESC = Type.getDescriptor(Removable.class);
	static final Set<Integer> PRIMITIVE_OPCODES = new HashSet<>();

	private final Set<String> deletedIfaces = new HashSet<>(); // удалённые интерфейсы
	private final Set<String> deletedFields = new HashSet<>(); // удалённые поля
	private final Map<String, String> deletedClasses = new HashMap<>(); // удалённые классы (ключ - имя, значение - имя родителя)

	static {
		PRIMITIVE_OPCODES.add(Opcodes.ACONST_NULL);
		PRIMITIVE_OPCODES.add(Opcodes.LDC);
		PRIMITIVE_OPCODES.add(Opcodes.SIPUSH); // int, long, short
		PRIMITIVE_OPCODES.add(Opcodes.BIPUSH); // byte
		PRIMITIVE_OPCODES.add(Opcodes.ICONST_0); // 0, false
		PRIMITIVE_OPCODES.add(Opcodes.ICONST_1); // 1, true
		PRIMITIVE_OPCODES.add(Opcodes.ICONST_2); // 2
		PRIMITIVE_OPCODES.add(Opcodes.ICONST_3); // 3
		PRIMITIVE_OPCODES.add(Opcodes.ICONST_4); // 4
		PRIMITIVE_OPCODES.add(Opcodes.ICONST_5); // 5
	}

	public void removeClasses(JarContents contents) {
		Iterator<Map.Entry<String, ClassNode>> it = contents.classes.entrySet().iterator();
		while(it.hasNext()) {
			ClassNode node = it.next().getValue();
			if(checkRemovable(node.invisibleAnnotations, true)) {
				if(Modifier.isInterface(node.access)) {
					deletedIfaces.add(node.name);
				} else {
					deletedClasses.put(node.name, node.superName);
				}
				it.remove();
				SimpleLogger.instance.info("Removed " + (Modifier.isInterface(node.access) ? "interface" : "class") + ": " + Utils.normalizeName(node.name));
			}
		}

		// Удаляем вложенные классы и подклассы
		contents.classes.entrySet().removeIf((Map.Entry<String, ClassNode> entry) -> {
			String name = entry.getValue().name;
			String parentName = name;
			while((parentName = Utils.getParentClassName(parentName)) != null) { // проверяем всех родителей, поднимаясь на уровень выше
				if(deletedClasses.containsKey(parentName)) {
					SimpleLogger.instance.info("Removed subclass: " + Utils.normalizeName(name));
					return true;  // удаляем, если родитель удалён
				}
			}
			return false;
		});
	}

	public void processClasses(JarContents contents, boolean applyFixes) {
		for(ClassNode node : contents.classes.values()) {
			deletedFields.clear();
			if(applyFixes) {
				checkInterfaces(node);
				checkSuperclass(node);
			}
			checkFields(node);
			checkMethods(node);
		}
	}

	/**
	 * Проверяет интерфейсы класса и убирает те, которые были удалены
	 * @param clazz Класс
	 */
	private void checkInterfaces(ClassNode clazz) {
		Iterator<String> it = clazz.interfaces.iterator();

		while(it.hasNext()) {
			String iFace = it.next();
			if(deletedIfaces.contains(iFace)) {
				it.remove();
				SimpleLogger.instance.info(String.format("Removed interface usage %s in %s", iFace, clazz.name));
			}
		}
	}

	/**
	 * Проверяет, были ли удалены родительские классы и перенаправлят так, чтобы восстановить цепочку наследования
	 * @param clazz Класс
	 */
	private void checkSuperclass(ClassNode clazz) {
		String oldSuper = clazz.superName;
		String superName = getSuperclass(oldSuper);
		if(!oldSuper.equals(superName)) {
			clazz.superName = superName;

			// Исправляем случаи использования в методах
			for(MethodNode method : (List<MethodNode>)clazz.methods) {
				ListIterator<AbstractInsnNode> itr = method.instructions.iterator();
				while(itr.hasNext()) {
					AbstractInsnNode insn = itr.next();
					switch(insn.getType()) {
						case AbstractInsnNode.FIELD_INSN:
							FieldInsnNode faccess = (FieldInsnNode)insn;
							if(faccess.owner.equals(oldSuper)) faccess.owner = superName;
							break;
						case AbstractInsnNode.METHOD_INSN:
							MethodInsnNode maccess = (MethodInsnNode)insn;
							if(maccess.owner.equals(oldSuper)) maccess.owner = superName;
							break;
						case AbstractInsnNode.TYPE_INSN:
							TypeInsnNode type = (TypeInsnNode)insn;
							if(type.desc.equals(oldSuper)) type.desc = superName;
							break;
					}
				}
			}

			SimpleLogger.instance.info(String.format("Changed superclass for %s: %s -> %s", Utils.normalizeName(clazz.name), Utils.normalizeName(oldSuper), Utils.normalizeName(superName)));
		}
	}

	/**
	 * Ищет родительский класс
	 * @param className Старый родительский класс
	 * @return Новый родительский класс
	 */
	private String getSuperclass(String className) {
		if(className == null || className.equals("java/lang/Object")) return className;

		String deletedSuper = deletedClasses.get(className);
		if(deletedSuper != null) return getSuperclass(deletedSuper);
		return className;
	}

	/**
	 * Проверяет поля
	 * @param clazz Класс
	 */
	private void checkFields(ClassNode clazz) {
		Iterator<FieldNode> it = clazz.fields.iterator();
		while(it.hasNext()) {
			FieldNode field = it.next();
			if(checkRemovable(field.invisibleAnnotations, true)) {
				deletedFields.add(field.name + DATA_SEPARATOR + field.desc);
				it.remove();
				SimpleLogger.instance.info("Removed field: " + Utils.normalizeName(clazz.name) + "." + field.name);
			}
		}
	}

	/**
	 * Проверяет методы
	 * @param clazz Класс
	 */
	private void checkMethods(ClassNode clazz) {
		Iterator<MethodNode> it = clazz.methods.iterator();
		while(it.hasNext()) {
			MethodNode method = it.next();

			// Убираем случаи использования удалённых полей в конструкторах (присвоение)
			if(method.name.equals(OBJECT_CONSTRUCTOR) || method.name.equals(STATIC_CONSTRUCTOR)) {
				this.checkConstructor(clazz, method);
			} else if(checkRemovable(method.invisibleAnnotations, true)) {
				it.remove();
				SimpleLogger.instance.info("Removed method: " + Utils.normalizeName(clazz.name) + "." + method.name + method.desc);
			}
		}
	}

	/**
	 * Удаляет случаи использования удалённых полей в конструкторе
	 * @param clazz Класс
	 * @param method Метод
	 */
	private void checkConstructor(ClassNode clazz, MethodNode method) {
		ListIterator<AbstractInsnNode> itr = method.instructions.iterator();
		final boolean isStatic = method.name.equals(STATIC_CONSTRUCTOR);

		while(itr.hasNext()) {
			AbstractInsnNode insn = itr.next();
			if(insn.getOpcode() == Opcodes.PUTFIELD || insn.getOpcode() == Opcodes.PUTSTATIC) {
				FieldInsnNode faccess = (FieldInsnNode)insn;
				if(deletedFields.contains(faccess.name + DATA_SEPARATOR + faccess.desc)) {

					boolean canRemovePrevious = false;
					boolean removeAloadThis = false;
					AbstractInsnNode valueLoadInsn = insn.getPrevious();
					if(valueLoadInsn != null) {
						canRemovePrevious = PRIMITIVE_OPCODES.contains(valueLoadInsn.getOpcode());
						if(!isStatic && canRemovePrevious) { // Поиск инструкции this для удаления
							AbstractInsnNode aload = valueLoadInsn.getPrevious();
							removeAloadThis = (aload != null && aload.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)aload).var == 0);
							if(!removeAloadThis) throw new InternalError("Can't find 'aload_0'");
						}
					}

					if(canRemovePrevious) {
						itr.remove(); // putfield
						itr.previous();
						itr.remove(); // переменная
						if(removeAloadThis) {
							itr.previous();
							itr.remove(); // this
						}
					} else {
						itr.set(new InsnNode(Opcodes.POP)); // для переменной
						if(!isStatic) itr.add(new InsnNode(Opcodes.POP)); // для this
					}
					SimpleLogger.instance.info("Removed field '" + faccess.name + "' usage in: " + Utils.normalizeName(clazz.name) + "." + method.name + method.desc);
				}
			}
		}
	}

	/**
	 * Проверяет наличие аннотации
	 * @param annotations Список аннотаций
	 * @param removeAnnotation Можно ли удалить аннотацию?
	 * @return true - если элемент помечен для удаления
	 */
	public static boolean checkRemovable(List<AnnotationNode> annotations, boolean removeAnnotation) {
		if(annotations != null && !annotations.isEmpty()) {
			Iterator<AnnotationNode> it = annotations.iterator();
			while(it.hasNext()) {
				AnnotationNode node = it.next();
				if(node.desc.equals(REMOVABLE_DESC) && !node.values.isEmpty()) {
					if(node.values.size() % 2 != 0) throw new InternalError("Bad AnnotationNode values count: " + node.values.size()); // Число должно быть чётным

					for(int i = 0; i < node.values.size(); i += 2) {
						String key = (String)node.values.get(i);
						Object value = node.values.get(i + 1);

						if(key.equals("remove")) {
							boolean remove = (boolean)value;
							if(!remove && removeAnnotation) it.remove(); // удаляем аннотацию
							return remove;
						}
					}
				}
			}
		}
		return false;
	}

}
