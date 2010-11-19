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

package org.eclipse.imp.formatting.editor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.ide.IDEActionFactory;
import org.eclipse.ui.part.EditorActionBarContributor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

/**
 * Manages the installation/deinstallation of global actions for multi-page
 * editors. Responsible for the redirection of global actions to the active
 * editor. Multi-page contributor replaces the contributors for the individual
 * editors in the multi-page editor.
 */
public class Contributor extends EditorActionBarContributor {
	private IEditorPart activeEditorPart;

	/**
	 * Creates a multi-page contributor.
	 */
	public Contributor() {
		super();
	}

	/**
	 * Returns the action registed with the given text editor.
	 * 
	 * @return IAction or null if editor is null.
	 */
	protected IAction getAction(ITextEditor editor, String actionID) {
		return (editor == null ? null : editor.getAction(actionID));
	}

	/*
	 * (non-JavaDoc) Method declared in
	 * AbstractMultiPageEditorActionBarContributor.
	 */

	public void setActivePage(IEditorPart part) {
		if (activeEditorPart == part)
			return;

		activeEditorPart = part;

		IActionBars actionBars = getActionBars();
		if (actionBars != null) {

			ITextEditor editor = (part instanceof ITextEditor) ? (ITextEditor) part
					: null;

			actionBars.setGlobalActionHandler(ActionFactory.DELETE.getId(),
					getAction(editor, ITextEditorActionConstants.DELETE));
			actionBars.setGlobalActionHandler(ActionFactory.UNDO.getId(),
					getAction(editor, ITextEditorActionConstants.UNDO));
			actionBars.setGlobalActionHandler(ActionFactory.REDO.getId(),
					getAction(editor, ITextEditorActionConstants.REDO));
			actionBars.setGlobalActionHandler(ActionFactory.CUT.getId(),
					getAction(editor, ITextEditorActionConstants.CUT));
			actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(),
					getAction(editor, ITextEditorActionConstants.COPY));
			actionBars.setGlobalActionHandler(ActionFactory.PASTE.getId(),
					getAction(editor, ITextEditorActionConstants.PASTE));
			actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(),
					getAction(editor, ITextEditorActionConstants.SELECT_ALL));
			actionBars.setGlobalActionHandler(ActionFactory.FIND.getId(),
					getAction(editor, ITextEditorActionConstants.FIND));
			actionBars.setGlobalActionHandler(
					IDEActionFactory.BOOKMARK.getId(), getAction(editor,
							IDEActionFactory.BOOKMARK.getId()));
			actionBars.updateActionBars();
		}
	}


	public void contributeToMenu(IMenuManager manager) {
		IMenuManager menu = new MenuManager("Editor &Menu");
		manager.prependToGroup(IWorkbenchActionConstants.MB_ADDITIONS, menu);
	}

	public void contributeToToolBar(IToolBarManager manager) {
		manager.add(new Separator());
		
		manager.add(new Action("Add rule") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.newRule();
				}
			}
		});
		manager.add(new Action("Add rule...") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.addRuleFromExample();
				}
			}
		});
		manager.add(new Action("Delete rule") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.deleteRule();
				}
			}
		});
		manager.add(new Action("Add separator") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.addSeparator();
				}
			}
		});
		manager.add(new Action("Up") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.moveUp();
				}
			}
		});
		manager.add(new Action("Down") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.moveDown();
				}
			}
		});
		manager.add(new Action("New option") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.addOption();
				}
			}
		});
		manager.add(new Action("Delete option") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.deleteOption();
				}
			}
		});
		manager.add(new Action("Format") {
			public void run() {
				IEditorPart editor = getPage().getActiveEditor();
				if (editor instanceof Editor) {
					Editor e = (Editor) editor;
					e.updateExample();
				}
			}
		});
// Disabled because Box formatter is not a part of The Meta-Environment yet
//		manager.add(new Action("Format rule") {
//			public void run() {
//				IEditorPart editor = getPage().getActiveEditor();
//				if (editor instanceof Editor) {
//					Editor e = (Editor) editor;
//					e.formatRule();
//				}
//			}
//		});
		
	}
}
