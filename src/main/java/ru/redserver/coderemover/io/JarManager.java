package ru.redserver.coderemover.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public final class JarManager {

	private static final Set<String> dirs = new LinkedHashSet<>();

	public static JarContents loadClassesFromJar(Path path) throws IOException {
		JarContents classCollection = new JarContents();
		try (JarInputStream jarInputStream = new JarInputStream(new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ)), false)) {
			JarEntry entry;
			while((entry = jarInputStream.getNextJarEntry()) != null) {
				if(entry.isDirectory()) continue;

				String name = entry.getName();
				byte[] bytes = readResource(jarInputStream);
				if(name.endsWith(".class")) {
					ClassReader reader = new ClassReader(bytes);
					ClassNode node = new ClassNode();
					reader.accept(node, 0);
					classCollection.classes.put(node.name, node);
				} else {
					classCollection.resources.put(name, bytes);
				}
			}
			classCollection.manifest = jarInputStream.getManifest();
		}

		return classCollection;
	}

	private static byte[] readResource(InputStream stream) throws IOException {
		ByteArrayOutputStream temp = new ByteArrayOutputStream(Math.max(8192, stream.available()));
		byte[] buffer = new byte[8192];
		int read;
		while((read = stream.read(buffer)) >= 0) {
			temp.write(buffer, 0, read);
		}
		return temp.toByteArray();
	}

	public static void writeClasssesToJar(Path path, JarContents classCollection) throws IOException {
		dirs.clear();
		try (JarOutputStream jarOutputStream = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
			if(classCollection.manifest != null) {
				addDirectories(JarFile.MANIFEST_NAME);
				jarOutputStream.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
				classCollection.manifest.write(jarOutputStream);
				jarOutputStream.closeEntry();
			}

			for(ClassNode clazz : classCollection.classes.values()) {
				addDirectories(clazz.name);

				ClassWriter writer = new ClassWriter(0);
				clazz.accept(writer);

				jarOutputStream.putNextEntry(new JarEntry(clazz.name.concat(".class")));
				jarOutputStream.write(writer.toByteArray());
				jarOutputStream.closeEntry();
			}

			for(Map.Entry<String, byte[]> entry : classCollection.resources.entrySet()) {
				addDirectories(entry.getKey());

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

	private static void addDirectories(String filePath) {
		int i = filePath.lastIndexOf('/');
		if(i >= 0) {
			String dirPath = filePath.substring(0, i);
			if(dirs.add(dirPath)) {
				addDirectories(dirPath);
			}
		}
	}

}
