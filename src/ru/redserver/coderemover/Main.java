package ru.redserver.coderemover;

import java.io.File;
import javassist.ClassPool;

public final class Main {

	private final String[] args;
	private final ClassPool pool = new ClassPool(true);

	public Main(String[] args) {
		this.args = args;
	}

	public static void main(String[] args) {
		(new Main(args)).run();
	}

	public void run() {
		try {
			if(args.length < 2) throw new IllegalArgumentException("Too few arguments: <input file> <output file>");
			File inputFile = new File(args[0]);
			if(!inputFile.exists() || !inputFile.isFile()) throw new IllegalArgumentException("Input file does not exists: " + inputFile);
		} catch (IllegalArgumentException ex) {
			System.out.println(ex.getMessage());
			System.exit(1);
		}
	}

}
