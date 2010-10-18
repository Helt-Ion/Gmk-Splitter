/*
 * Copyright (C) 2010 Medo <smaxein@googlemail.com>
 * 
 * This file is part of GmkSplitter.
 * GmkSplitter is free software and comes with ABSOLUTELY NO WARRANTY.
 * See LICENSE for details.
 */
package com.ganggarrison.gmdec.files;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.lateralgm.components.impl.ResNode;
import org.lateralgm.file.GmFile;
import org.lateralgm.resources.Resource;

import com.ganggarrison.gmdec.DeferredReferenceCreatorNotifier;
import com.ganggarrison.gmdec.FileTools;

public abstract class FileTreeFormat<T> {
	public abstract void write(File path, T resource, GmFile gmf) throws IOException;

	/**
	 * Add a resource into the resource tree.
	 * 
	 * @param resource
	 *            A resource read by this format
	 * @param parent
	 *            The parent node for this resource
	 */
	public abstract void addResToTree(T resource, ResNode parent);

	/**
	 * Add all resources of the type read by this format into the GmFile. This
	 * method must be called with all resources of that type that should go into
	 * the GmFile, so that ID conflicts can be properly resolved.
	 */
	public abstract void addAllResourcesToGmFile(List<T> resources, GmFile gmf);

	public abstract T read(File path, String resourceName, DeferredReferenceCreatorNotifier drcn)
			throws IOException;

	protected String defaultFilestring(Resource<?, ?> resource) throws IOException {
		return defaultFilestring(resource.getName());
	}

	protected String defaultFilestring(String name) throws IOException {
		if (FileTools.isGoodFilename(name)) {
			return name;
		}
		throw new IOException("Ressource name \"" + name + "\" can't be used as a filename, aborting.");
	}

	protected File getXmlFile(File path, Resource<?, ?> resource) throws IOException {
		return new File(path, defaultFilestring(resource) + ".xml");
	}

	protected File getXmlFile(File path, String resourceName) throws IOException {
		return new File(path, defaultFilestring(resourceName) + ".xml");
	}
}
