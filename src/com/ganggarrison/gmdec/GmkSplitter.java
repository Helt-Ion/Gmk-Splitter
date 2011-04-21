/*
 * Copyright (C) 2010 Medo <smaxein@googlemail.com>
 * 
 * This file is part of GmkSplitter.
 * GmkSplitter is free software and comes with ABSOLUTELY NO WARRANTY.
 * See LICENSE for details.
 */
package com.ganggarrison.gmdec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lateralgm.components.impl.ResNode;
import org.lateralgm.file.GmFile;
import org.lateralgm.file.GmFileReader;
import org.lateralgm.file.GmFileWriter;
import org.lateralgm.file.GmFormatException;
import org.lateralgm.resources.library.LibManager;
import org.lateralgm.resources.sub.Constant;

import com.ganggarrison.easyxml.XmlReader;
import com.ganggarrison.gmdec.files.IncludedFileFormat;
import com.ganggarrison.gmdec.xml.ConstantsXmlFormat;

public class GmkSplitter {
	private static final String CONSTANTS_FILENAME = "Constants.xml";

	public enum IdPreservation {
		NONE, OBJECTS, ALL
	};

	public static boolean convertLineEndings = true;
	public static boolean omitDisabledFields = true;
	public static IdPreservation preserveIds = IdPreservation.OBJECTS;

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			printUsage();
			return;
		}

		if (isGmkFile(args[0])) {
			File gmkFile = new File(args[0]);
			File dir = new File(args[1]);
			if (!gmkFile.isFile()) {
				System.err.println("Source file " + gmkFile + " not found.");
				return;
			}

			if (dir.exists()) {
				System.err.println("Destination directory " + dir + " already exists.");
				return;
			}

			decompose(gmkFile, dir);
		} else if (isGmkFile(args[1])) {
			File dir = new File(args[0]);
			File gmkFile = new File(args[1]);
			if (!dir.isDirectory()) {
				System.err.println("Source directory " + dir + " not found.");
				return;
			}

			if (gmkFile.exists()) {
				System.err.println("Destination file " + gmkFile + " already exists.");
				return;
			}

			compose(dir, gmkFile);
		} else {
			printUsage();
		}
	}

	private static void printUsage() {
		System.out.println("Usage: java -jar GmkSplit.jar <source> <destination>");
		System.out.println("One of <source> or <destination> must be the name of a .gmk-file and end with '.gmk'.");
		System.out.println("The destination must not already exist. This tool won't overwrite.");
	}

	private static boolean isGmkFile(String arg) {
		return arg.toLowerCase().endsWith(".gmk") || arg.toLowerCase().endsWith(".gm81");
	}

	public static void decompose(File sourceGmk, File destinationPath) throws IOException {
		LibManager.autoLoad();
		try {
			ResNode root = new ResNode("Root", (byte) 0, null, null);
			GmFile gmf = GmFileReader.readGmFile(sourceGmk.getAbsolutePath(), root);
			if (gmf.fileVersion != 800 && gmf.fileVersion != 810) {
				System.err
						.println("Warning: The source .gmk file is not of GM version 8. GMK Splitter is *not tested* with this format.");
			}
			ResourceWriter.writeTree(root, gmf, destinationPath);

			writeConstants(gmf, destinationPath);
			writeIncludedFiles(gmf, destinationPath);
		} catch (GmFormatException e) {
			throw new IOException(e);
		}
	}

	public static void compose(File sourcePath, File destinationGmk) throws IOException {
		LibManager.autoLoad();
		GmFile gmf = new GmFile();
		gmf.filename = destinationGmk.getAbsolutePath();
		gmf.fileVersion = 800;
		ResNode root = new ResNode("Root", (byte) 0, null, null);
		new ResourceReader().readTree(root, gmf, sourcePath);

		readConstants(gmf, sourcePath);
		readIncludedFiles(gmf, sourcePath);

		GmFileWriter.writeGmFile(gmf, root);
	}

	private static void writeConstants(GmFile gmf, File destinationPath) throws IOException {
		File constantsFile = new File(destinationPath, CONSTANTS_FILENAME);
		new ConstantsXmlFormat().write(gmf.constants, constantsFile);
	}

	private static void readConstants(GmFile gmf, File sourcePath) throws IOException {
		File constantsFile = new File(sourcePath, CONSTANTS_FILENAME);
		List<Constant> constants = new ConstantsXmlFormat().read(new XmlReader(constantsFile));
		gmf.constants = new ArrayList<Constant>(constants);
	}

	private static String INCLUDED_FILES_DIR = "Included Files";

	private static void writeIncludedFiles(GmFile gmf, File destinationPath) throws IOException {
		if (!gmf.includes.isEmpty()) {
			File includedFilesPath = new File(destinationPath, INCLUDED_FILES_DIR);
			if (!includedFilesPath.mkdirs()) {
				throw new IOException("Unable to create path: " + includedFilesPath);
			}
			IncludedFileFormat.write(includedFilesPath, gmf.includes);
		}
	}

	private static void readIncludedFiles(GmFile gmf, File sourcePath) throws IOException {
		File includedFilesPath = new File(sourcePath, INCLUDED_FILES_DIR);
		if (includedFilesPath.isDirectory()) {
			IncludedFileFormat.read(includedFilesPath, gmf);
		}
	}
}
