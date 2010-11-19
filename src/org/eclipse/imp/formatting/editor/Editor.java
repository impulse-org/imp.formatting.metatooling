/*******************************************************************************
* Copyright (c)IBM Corporation 2008 
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Jurgen Vinju (jurgenv@cwi.nl) - initial API and implementation
*******************************************************************************/

package org.eclipse.imp.formatting.editor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.imp.box.interpreter.BoxInterpreter;
import org.eclipse.imp.box.parser.BoxParseController;
import org.eclipse.imp.box.parser.Ast.IBox;
import org.eclipse.imp.formatting.spec.ExtensionPointBinder;
import org.eclipse.imp.formatting.spec.ParseException;
import org.eclipse.imp.formatting.spec.Parser;
import org.eclipse.imp.formatting.spec.Specification;
import org.eclipse.imp.formatting.spec.Transformer;
import org.eclipse.imp.formatting.spec.Unparser;
import org.eclipse.imp.language.Language;
import org.eclipse.imp.language.LanguageRegistry;
import org.eclipse.imp.model.ISourceProject;
import org.eclipse.imp.model.ModelFactory;
import org.eclipse.imp.utils.DynamicBundleUtils;
import org.eclipse.imp.utils.ExtensionPointUtils;
import org.eclipse.imp.utils.SavingMessageHandler;
import org.eclipse.imp.utils.StreamUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.osgi.framework.Bundle;

/**
 * This is the start of a prototype generic formatting tool for IMP.
 * 
 * This class is the main editor view for formatting specifications. A
 * formatting specification will be stored in an XML file containing the name of
 * the language that is to be formatted, a list of formatting rules and an
 * example program.
 * 
 * The rules are now Box expressions, out of which the source code is to be
 * extracted and parsed to form patterns. The Box expressions are to be parsed
 * too. A formatting rule defines the mapping from source code to Box.
 * 
 * The Box rules are continuously applied to the example source code such that
 * the user can see the effect of the specification.
 */
public class Editor extends MultiPageEditorPart implements IResourceChangeListener {

	private static final int RuleEditorIndex = 0;

	private static final int ExampleEditorIndex = 1;

	private static final int OptionEditorIndex = 2;
	
	protected TextEditor fEditor;

	protected Text fExampleText;

	private Specification fModel;

	private boolean fExampleModified = false;

	private Parser fParser;

	private RuleTable fRuleTable;

	private SpaceOptionTable fSpaceTable;

	private ModifyListener fExampleModifyListener;

	private Font fSampleFont;

	private Color fErrorColor;

	private Color fNormalColor;

	/**
	 * The dynamically-activated Bundle containing the language descriptor for the language
	 * whose formatter is being edited. If non-null, this will invariably refer to a workspace
	 * project. Saved so it can be dynamically uninstalled when the editor closes, if needed.
	 */
    private Bundle fLanguageProjectBundle;

    /**
     * The dynamically-activated Bundle containing the formatting extension for the language
     * whose formatter is being edited. If non-null, this will invariably refer to a workspace
     * project. Saved so it can be dynamically uninstalled when the editor closes, if needed.
     */
    private Bundle fFormattingProjectBundle;

	public Editor() {
		super();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this);
		LanguageRegistry.getLanguages();
	}

	public Specification getModel() {
		return fModel;
	}

	public void createExampleViewer() {
		Composite parent = new Composite(getContainer(), SWT.NONE);
		parent.setLayout(new FillLayout());
		fExampleText = new Text(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);

		fSampleFont= new Font(fExampleText.getDisplay(), "Monospace", 14, 0);

		fExampleText.setFont(fSampleFont);

		fExampleModifyListener = new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				fModel.setExample(fExampleText.getText());
				fExampleModified = true;
				firePropertyChange(PROP_DIRTY);

				Object ast = fParser.parseObject(fExampleText.getText());
				if (ast != null) {
					fModel.setExampleAst(ast);
				}
			}
		};
		fExampleText.addModifyListener(fExampleModifyListener);
		fExampleText.addKeyListener(new KeyListener() {
            public void keyReleased(KeyEvent e) {
                if (e.keyCode == 13 && (e.stateMask & (SWT.COMMAND | SWT.CTRL)) != 0) {
                    reformatExample();
                }
            }

            public void keyPressed(KeyEvent e) { }
        });

		addPage(ExampleEditorIndex, parent);
		setPageText(ExampleEditorIndex, "Example");
	}

	protected void updateExample() {
		if (fModel != null) {
			String current = fExampleText.getText();

			if (current == null || current.length() == 0) {
				fExampleText.setText(fModel.getExample());
			}
			reformatExample();
		}
	}

	protected void createPages() {
		createRuleEditor();
        fErrorColor= new Color(fRuleTable.getSite().getWorkbenchWindow().getShell().getDisplay(), 255, 128, 128);
        fNormalColor= new Color(fRuleTable.getSite().getWorkbenchWindow().getShell().getDisplay(), 255, 255, 255);

		IEditorInput input = fRuleTable.getEditorInput();

		setPartName(input.getName());

		fModel = updateModelFromFile(input);

		createExampleViewer();
		createOptionEditor();

		fRuleTable.setModel(fModel);
		fSpaceTable.setModel(fModel);
		updateExample();

		fExampleModified = false;
	}

	private void createOptionEditor() {
		fSpaceTable = new SpaceOptionTable(fModel);
		fSpaceTable.addPropertyListener(new IPropertyListener() {
			public void propertyChanged(Object source, int propId) {
				firePropertyChange(PROP_DIRTY);
			}
		});

		try {
			addPage(OptionEditorIndex, fSpaceTable, getEditorInput());
			setPageText(OptionEditorIndex, fSpaceTable.getTitle());

		} catch (PartInitException e) {
			e.printStackTrace();
		}
	}

	private void createRuleEditor() {
		fRuleTable = new RuleTable();
		
		fRuleTable.addPropertyListener(new IPropertyListener() {
			public void propertyChanged(Object source, int propId) {
				firePropertyChange(PROP_DIRTY);
			}
		});
		
		try {
			addPage(RuleEditorIndex, fRuleTable, getEditorInput());
			setPageText(RuleEditorIndex, fRuleTable.getTitle());
			
		} catch (PartInitException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

    private Specification updateModelFromFile(IEditorInput input) {
		try {
            IFile file = ((IFileEditorInput) input).getFile();
			IPath path = file.getProjectRelativePath();
			IProject project = file.getProject();
			IPath fullFilePath = project.getLocation().append(path);
			ISourceProject sp = ModelFactory.open(project);

			fParser = new Parser(fullFilePath, sp, new SavingMessageHandler());

			String editorText = StreamUtils.readStreamContents(file.getContents());

			if (editorText == null || editorText.length() == 0) {
			    String langName= ExtensionPointUtils.discoverLanguageForProject(project);

			    if (langName == null) {
			        langName= askUserForLanguage();
			    }

				if (langName.length() > 0) {
				    activateWorkspaceBundles(langName);
				    fParser.setLanguage(langName);

				    Specification skelSpec= new Specification(langName, fParser);

				    this.fExampleModified= true; // mark dirty
				    return skelSpec;
				} else {
				    return new Specification("", fParser);
				}
			} else {
			    // Activate bundles as needed to make sure the parser can run properly
			    Pattern pat= Pattern.compile("<language>([a-zA-Z0-9_]+)</language>");
			    Matcher m= pat.matcher(editorText);

			    if (m.find()) {
			        String langName= m.group(1);

			        activateWorkspaceBundles(langName);
			    }
			    fParser.getMessageHandler().clearMessages();
			    fModel = fParser.load(editorText);
			    fParser.getMessageHandler().endMessages();
			    // TODO perhaps place messages on the model?
			    return fModel;
			}
		} catch (ParseException e) {
		    SavingMessageHandler smh= (SavingMessageHandler) fParser.getMessageHandler();
			System.err.println("error:" + smh.getConcatenatedMessages());
		} catch (ModelFactory.ModelException e) {
			System.err.println("model error:" + e);
		} catch (CoreException e) {
			System.err.println("file reading error:" + e);
		}

		return new Specification(fParser);
	}

	private String askUserForLanguage() {
		InputDialog d= new InputDialog(fRuleTable.getSite().getShell(), "Missing language ID", "Please provide the language ID", "",
				new IInputValidator() {
					public String isValid(String newText) {
						return (newText != null && newText.length() > 0) ? null : "Language ID must be non-empty";
					}
				});

		if (d.open() == Dialog.OK) {
			return d.getValue();
		}
		return "";
	}

	private void activateWorkspaceBundles(String langName) {
	    // Note: the extensions for the languageDescription and the formattingDescription may
	    // reside in the same bundle. If so, that's ok; activateWorkspaceBundleForExtension()
	    // is a no-op if the bundle in question is already loaded/activated.
        fLanguageProjectBundle= DynamicBundleUtils.activateWorkspaceBundleForLanguage(langName);
        if (fLanguageProjectBundle != null) {
            fFormattingProjectBundle= DynamicBundleUtils.activateWorkspaceBundleForExtension(langName, "org.eclipse.imp.formatting.formattingSpecification");
        } else {
            fFormattingProjectBundle= null;
        }
    }

    @SuppressWarnings("unused")
    private void deactivateWorkspaceBundles() {
        if (fLanguageProjectBundle != null) {
            DynamicBundleUtils.deactivateWorkspaceBundle(fLanguageProjectBundle);
            fLanguageProjectBundle= null;
        }
        if (fFormattingProjectBundle != null) {
            DynamicBundleUtils.deactivateWorkspaceBundle(fFormattingProjectBundle);
            fFormattingProjectBundle= null;
        }
    }

    private void reformatExample() {
        fParser.getMessageHandler().clearMessages();

        String exampleStr= fModel.getExample();
		Object ast = fParser.parseObject(exampleStr);

		if (ast != null) {
			fModel.setExampleAst(ast);

			try {
				Language objectLanguage = LanguageRegistry.findLanguage(fModel.getLanguage());

				activateWorkspaceBundles(objectLanguage.getName());

				ExtensionPointBinder b = new ExtensionPointBinder(objectLanguage);

				Transformer t = new Transformer(fModel, b.getASTAdapter());
				String boxStr = t.transformToBox(exampleStr, fModel.getExampleAst());
				IBox box= BoxParseController.parseBox(boxStr);
				BoxInterpreter bi = new BoxInterpreter();
                String newExample = bi.interpret(box);

				fExampleText.removeModifyListener(fExampleModifyListener);
				fExampleText.setText(newExample);
				fExampleText.addModifyListener(fExampleModifyListener);
	            fExampleText.append(ast.toString());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO: something useful
				e.printStackTrace();
			}
			fExampleText.setBackground(fNormalColor);
		} else {
		    SavingMessageHandler smh= (SavingMessageHandler) fParser.getMessageHandler();
		    String allMsgs= smh.getConcatenatedMessages();
		    fExampleText.setToolTipText(allMsgs);
		    fExampleText.setBackground(fErrorColor);
		}
	}

	public void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		deactivateWorkspaceBundles();
		fSampleFont.dispose();
		fErrorColor.dispose();
		fNormalColor.dispose();
		super.dispose();
	}

	public void doSave(IProgressMonitor monitor) {
		try {
			Unparser u = new Unparser();
			String contents = u.unparse(fModel);
			
			IFile file = ((FileEditorInput) getEditorInput()).getFile();
			InputStream s = new ByteArrayInputStream(contents.getBytes());
			file.setContents(s, 0, monitor);
			
			fRuleTable.setDirty(false);
			fSpaceTable.setDirty(false);
			fExampleModified = false;

			firePropertyChange(PROP_DIRTY);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	public void doSaveAs() {
		// not allowed
	}
	
	public boolean isSaveAsAllowed() {
		return false;
	}

	public void gotoMarker(IMarker marker) {
		setActivePage(0);
		IDE.gotoMarker(getEditor(0), marker);
	}

	public void init(IEditorSite site, IEditorInput editorInput) throws PartInitException {
		if (!(editorInput instanceof IFileEditorInput))
			throw new PartInitException("Invalid Input: Must be IFileEditorInput");
		super.init(site, editorInput);
	}

	public void resourceChanged(final IResourceChangeEvent event) {
		if (event.getType() == IResourceChangeEvent.PRE_CLOSE) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					IWorkbenchPage[] pages = getSite().getWorkbenchWindow().getPages();
					for (int i = 0; i < pages.length; i++) {
						if (((FileEditorInput) fEditor.getEditorInput()).getFile().getProject().equals(event.getResource())) {
							IEditorPart editorPart = pages[i].findEditor(fEditor.getEditorInput());
							pages[i].closeEditor(editorPart, true);
						}
					}
				}
			});
		}
	}

	protected void pageChange(int newPageIndex) {
		switch (newPageIndex) {
		case OptionEditorIndex:
			fSpaceTable.refresh();
			break;
		case ExampleEditorIndex:
			updateExample();
			break;
		case RuleEditorIndex:
			// ruleTable.refresh(); too 
//		case PlainEditorIndex:
		}
		
		super.pageChange(newPageIndex);
	}
	
	public boolean isDirty() {
		return fRuleTable.isDirty() || fSpaceTable.isDirty() || fExampleModified;
	}

	public void newRule() {
		fRuleTable.newRule();
	}
	
	public void addSeparator() {
		fRuleTable.addSeparator();
	}

	public void deleteRule() {
	   fRuleTable.deleteRule();
	}

	public void formatRule() {
		fRuleTable.formatRule();
	}

	public void addRuleFromExample() {
		fRuleTable.addRuleFromExample();
	}

	public void moveUp() {
		fRuleTable.move(-1);
	}
	
	public void moveDown() {
		fRuleTable.move(1);
	}

	public void addOption() {
		fSpaceTable.newOption();
	}

	public void deleteOption() {
		fSpaceTable.deleteOption();
	}
}
