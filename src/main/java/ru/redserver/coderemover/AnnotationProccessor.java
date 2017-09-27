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

/**
 * Обрабатывает аннотации
 * @author Nuclear
 */
public final class AnnotationProccessor {

	static final String DATA_SEPARATOR = "<::>";
	static final String REMOVABLE_DESC = Type.getDescriptor(Removable.class);

	private final Set<String> deletedIfaces = new HashSet<>(); // удалённые интерфейсы
	private final Set<String> deletedFields = new HashSet<>(); // удалённые поля
	private final Map<String, String> deletedClasses = new HashMap<>(); // удалённые классы (ключ - имя, значение - имя родителя)

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
				CodeRemover.LOG.info("Removed " + (Modifier.isInterface(node.access) ? "interface" : "class") + ": " + Utils.normalizeName(node.name));
			}
		}

		// Удаляем вложенные классы и подклассы
		contents.classes.entrySet().removeIf((Map.Entry<String, ClassNode> entry) -> {
			String name = entry.getValue().name;
			String parentName = name;
			while((parentName = Utils.getParentClassName(parentName)) != null) { // проверяем всех родителей, поднимаясь на уровень выше
				if(deletedClasses.containsKey(parentName)) {
					CodeRemover.LOG.info("Removed subclass: " + Utils.normalizeName(name));
					return true;  // удаляем, если родитель удалён
				}
			}
			return false;
		});
	}

	public void processClasses(JarContents contents) {
		for(ClassNode node : contents.classes.values()) {
			deletedFields.clear();
			checkInterfaces(node);
			checkSuperclass(node);
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
				CodeRemover.LOG.info(String.format("Removed interface usage %s in %s", iFace, clazz.name));
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

			CodeRemover.LOG.info(String.format("Changed superclass for %s: %s -> %s", Utils.normalizeName(clazz.name), Utils.normalizeName(oldSuper), Utils.normalizeName(superName)));
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
				CodeRemover.LOG.info("Removed field: " + Utils.normalizeName(clazz.name) + "." + field.name);
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
			if(method.name.equals("<init>") || method.name.equals("<clinit>")) {
				ListIterator<AbstractInsnNode> itr = method.instructions.iterator();
				while(itr.hasNext()) {
					AbstractInsnNode insn = itr.next();
					if(insn.getOpcode() == Opcodes.PUTFIELD || insn.getOpcode() == Opcodes.PUTSTATIC) {
						FieldInsnNode faccess = (FieldInsnNode)insn;
						if(deletedFields.contains(faccess.name + DATA_SEPARATOR + faccess.desc)) {
							itr.set(new InsnNode(Opcodes.POP));
							CodeRemover.LOG.info("Removed field '" + faccess.name + "' usage in: " + Utils.normalizeName(clazz.name) + "." + method.name + method.desc);
						}
					}
				}
			} else if(checkRemovable(method.invisibleAnnotations, true)) {
				it.remove();
				CodeRemover.LOG.info("Removed method: " + Utils.normalizeName(clazz.name) + "." + method.name + method.desc);
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
