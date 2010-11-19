/*******************************************************************************
* Copyright (c) IBM Corporation 2008 
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Jurgen Vinju (jurgenv@cwi.nl) - initial API and implementation
*******************************************************************************/

package org.eclipse.imp.formatting.builders;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.imp.builder.BuilderBase;
import org.eclipse.imp.formatting.Activator;
import org.eclipse.imp.formatting.spec.Parser;
import org.eclipse.imp.language.Language;
import org.eclipse.imp.language.LanguageRegistry;
import org.eclipse.imp.model.ISourceProject;
import org.eclipse.imp.runtime.PluginBase;
import org.eclipse.imp.utils.NullMessageHandler;

/**
 * @author
 */
public class FormattingSpecificationBuilder extends BuilderBase {
	/**
	 * Extension ID of the FormattingSpecification builder. Must match the ID in
	 * the corresponding extension definition in plugin.xml. SMS 22 Mar 2007: If
	 * that ID is set through the NewBuilder wizard, then so must this one be.
	 */
	public static final String BUILDER_ID = Activator.kPluginID + ".org.eclipse.imp.formatting.builder";

	public static final String PROBLEM_MARKER_ID = Activator.kPluginID + ".org.eclipse.imp.formatting.builder.problem";

	// SMS 11 May 2006
	public static final String LANGUAGE_NAME = "FormattingSpecification";

	public static final Language LANGUAGE = LanguageRegistry.findLanguage(LANGUAGE_NAME);

	protected PluginBase getPlugin() {
		return Activator.getInstance();
	}

	protected String getErrorMarkerID() {
		return PROBLEM_MARKER_ID;
	}

	protected String getWarningMarkerID() {
		return PROBLEM_MARKER_ID;
	}

	protected String getInfoMarkerID() {
		return PROBLEM_MARKER_ID;
	}

	protected boolean isSourceFile(IFile file) {
		IPath path = file.getRawLocation();
		if (path == null)
			return false;

		String pathString = path.toString();
		if (pathString.indexOf("/bin/") != -1)
			return false;

		return LANGUAGE.hasExtension(path.getFileExtension());
	}

	/**
	 * @return true iff the given file is a source file that this builder should
	 *         scan for dependencies, but not compile as a top-level compilation
	 *         unit.<br>
	 *         <code>isNonRootSourceFile()</code> and
	 *         <code>isSourceFile()</code> should never return true for the
	 *         same file.
	 */
	protected boolean isNonRootSourceFile(IFile resource) {
		// TODO: If your language has non-root source files (e.g., header
		// files), then
		// reimplement this method to test for those
		System.err.println("FormattingSpecificationBuilder.isNonRootSourceFile(..) returning FALSE by default");
		return false;
	}

	/**
	 * Collects compilation-unit dependencies for the given file, and records
	 * them via calls to <code>fDependency.addDependency()</code>.
	 */
	protected void collectDependencies(IFile file) {
		// TODO: If your langauge has inter-file dependencies then reimplement
		// this method to collect those
		System.err.println("FormattingSpecificationBuilder.collectDependencies(..) doing nothing by default");
		return;
	}

	protected boolean isOutputFolder(IResource resource) {
		return resource.getFullPath().lastSegment().equals("bin");
	}

	protected void compile(final IFile file, IProgressMonitor monitor) {
		try {
			System.out.println("Builder.compile with file = " + file.getName());

			runParserForCompiler(file, monitor);

			doRefresh(file.getParent());
		} catch (Exception e) {
			getPlugin().writeErrorMsg(e.getMessage());

			e.printStackTrace();
		}
	}

	protected void runParserForCompiler(final IFile file,
			IProgressMonitor monitor) {
		try {
			// TODO bind extension points here to!
			IPath path = file.getProjectRelativePath();
			Parser p = new Parser(path, (ISourceProject) file.getProject(), new NullMessageHandler());

			try {
				p.parse(path);
			} catch (Exception e) {
				// TODO change exception type and do something serious with this
			}

			doRefresh(file.getParent());
		} catch (Exception e) {
			getPlugin().writeErrorMsg(e.getMessage());
			e.printStackTrace();
		}
	}
}
