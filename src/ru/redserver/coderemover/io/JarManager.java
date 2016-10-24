package ru.redserver.coderemover.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import javassist.CannotCompileException;
import javassist.CtClass;
import ru.redserver.coderemover.ClassCollection;
import ru.redserver.coderemover.CodeRemover;

/**
 *
 * @author Nuclear
 */
public class JarManager {

	public static ClassCollection loadClassesFromJar(File jar) throws IOException {
		ClassCollection classCollection = new ClassCollection();
		JarInputStream jarInputStream = new JarInputStream(new FileInputStream(jar), false);

		JarEntry entry;
		while((entry = jarInputStream.getNextJarEntry()) != null) {
			if(entry.isDirectory()) continue;

			String name = entry.getName();
			if(name.endsWith(".class")) {
				CtClass clazz = CodeRemover.CLASS_POOL.makeClass(jarInputStream);
				classCollection.getClasses().add(clazz);
			} else {
				ByteArrayOutputStream temp = new ByteArrayOutputStream(Math.max(8192, jarInputStream.available()));
				byte[] buffer = new byte[8192];
				int read;

				while((read = jarInputStream.read(buffer)) >= 0) {
					temp.write(buffer, 0, read);
				}

				classCollection.getExtraFiles().put(name, temp.toByteArray());
			}
		}
		classCollection.setManifest(jarInputStream.getManifest());

		return classCollection;
	}

	public static void writeClasssesToJar(File jar, ClassCollection classCollection) throws IOException, CannotCompileException {

		Set<String> dirs = new HashSet<>();
		try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jar))) {
			if(classCollection.getManifest() != null) {
				addDirectories(JarFile.MANIFEST_NAME, dirs);
				jarOutputStream.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
				classCollection.getManifest().write(jarOutputStream);
				jarOutputStream.closeEntry();
			}

			for(CtClass clazz : classCollection.getClasses()) {
				String classPath = clazz.getName().replace('.', '/');
				addDirectories(classPath, dirs);

				jarOutputStream.putNextEntry(new JarEntry(classPath.concat(".class")));
				jarOutputStream.write(clazz.toBytecode());
				jarOutputStream.closeEntry();
			}

			for(Map.Entry<String, byte[]> entry : classCollection.getExtraFiles().entrySet()) {
				addDirectories(entry.getKey(), dirs);

				jarOutputStream.putNextEntry(new JarEntry(entry.getKey()));
				jarOutputStream.write(entry.getValue());
				jarOutputStream.closeEntry();
			}

			for(String dirPath : dirs) {
				jarOutputStream.putNextEntry(new JarEntry(dirPath + "/"));
				jarOutputStream.closeEntry();
			}

			jarOutputStream.flush();
		}
	}

	private static void addDirectories(String filePath, Set<String> dirs) {
		int i = filePath.lastIndexOf('/');
		if(i >= 0) {
			String dirPath = filePath.substring(0, i);
			if(dirs.add(dirPath)) {
				addDirectories(dirPath, dirs);
			}
		}
	}

}
