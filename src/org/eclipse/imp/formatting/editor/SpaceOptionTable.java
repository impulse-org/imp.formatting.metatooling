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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.imp.formatting.spec.Specification;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;

public class SpaceOptionTable implements IEditorPart {
	private Table optionTable;

	private final int NAME_COLUMN = 0;
	private final int VALUE_COLUMN = 1;
	
	private static final String NAME_PROPERTY = "name";
    private static final String VALUE_PROPERTY = "value";
	  
	private TableItem activeOption;
	
	private List<IPropertyListener> listeners;
	
	private Specification model;
	
	private boolean dirty = false;
	private IEditorSite site;
	private IEditorInput input;
	private TableViewer tableViewer;

	public SpaceOptionTable(Specification model) {
		this.model = model;
		listeners = new LinkedList<IPropertyListener>();
	}
	
	public void setModel(Specification model) {
		this.model = model;
		refresh();
	}
	
    public void addPropertyListener(IPropertyListener l) {
        listeners.add(l);
    }
    
    private void firePropertyChange(int change) {
    	for (IPropertyListener l : listeners) {
    		l.propertyChanged(this, change);
    	}
    }
    
    public IEditorInput getEditorInput() {
		return input;
	}

	public IEditorSite getEditorSite() {
		return site;
	}

	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		this.site = site;
		this.input = input;
	}

	public void dispose() {
		optionTable.dispose();
	}

	public IWorkbenchPartSite getSite() {
		return site;
	}

	public String getTitle() {
		return "Options";
	}

	public Image getTitleImage() {
		return null;
	}

	public String getTitleToolTip() {
		return null;
	}

	public void removePropertyListener(IPropertyListener listener) {
		listeners.remove(listener);
	}

	public void setFocus() {
		optionTable.setFocus();
	}

	public Object getAdapter(Class adapter) {
		return null;
	}

	public void doSave(IProgressMonitor monitor) {
		throw new UnsupportedOperationException("not implemented");
	}

	public void doSaveAs() {
		throw new UnsupportedOperationException("not implemented");
	}

	public boolean isSaveAsAllowed() {
		return false;
	}

	public boolean isSaveOnCloseNeeded() {
		return false;
	}
    
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout(SWT.VERTICAL));
		
		optionTable = new Table(parent, SWT.FULL_SELECTION);
		optionTable.setLinesVisible(true);
		optionTable.setHeaderVisible(true);

		TableColumn name = new TableColumn(optionTable, SWT.NONE);
		name.setText("Key");
		name.setResizable(true);

		TableColumn value = new TableColumn(optionTable, SWT.NONE);
		value.setText("Value");
		value.setResizable(true);

		tableViewer = new TableViewer(optionTable);
		 
		name.pack();
		value.pack();
	
		createCellEditor();
	}
	
	private void createCellEditor() {
		tableViewer.setCellModifier(new ICellModifier() {
		      public boolean canModify(Object element, String property) {
		        return true;
		      }

		      public Object getValue(Object element, String property) {
		        if (NAME_PROPERTY.equals(property)) {
		          return ((EditableTableItem) element).getName();
		        }
		        else {
		          return ((EditableTableItem) element).getValue().toString();
		        }
		      }
		      
		      public void modify(Object element, String property, Object value) {
		        TableItem tableItem = (TableItem) element;
		        EditableTableItem data = (EditableTableItem) tableItem
		            .getData();
		        if (NAME_PROPERTY.equals(property)) {
					data.setName(value.toString());
				} else {
					data.setValue(Integer.parseInt(value.toString()));
				}
		        
		        setDirty(true);

				tableViewer.refresh(data);
			}
		    });
		
		    CellEditor nameEditor = new TextCellEditor(optionTable, SWT.BORDER);
		    ((Text) nameEditor.getControl()).addVerifyListener(new VerifyListener() {
				public void verifyText(VerifyEvent e) {
					int start = 0;
					e.doit = true;
					
					if (e.start == 0) {
						e.doit = e.text.startsWith("$");
						start = 1;
					}
						
					for (int i = start; i < e.text.length() && e.doit; i++) {
					  e.doit = Character.isLetter(e.text.charAt(i));
					}
				}
		    });
		    
		    
		    CellEditor valueEditor = new TextCellEditor(optionTable, SWT.BORDER);
		    ((Text) valueEditor.getControl()).addVerifyListener(new VerifyListener() {
				public void verifyText(VerifyEvent e) {
					e.doit = true;
					for (int i = 0; i < e.text.length() && e.doit; i++) {
				       e.doit = Character.isDigit(e.text.charAt(i));
					}
				}
		    });

		    tableViewer.setCellEditors(new CellEditor[] {nameEditor, valueEditor });
		    
		    

		    tableViewer
		        .setColumnProperties(new String[] { NAME_PROPERTY,
		            VALUE_PROPERTY });
		    
		    tableViewer.setLabelProvider(new ITableLabelProvider() {
				public Image getColumnImage(Object element, int columnIndex) {
					return null;
				}

				public String getColumnText(Object element, int columnIndex) {
					switch (columnIndex) {
					case NAME_COLUMN:
						return ((EditableTableItem) element).getName();
					case VALUE_COLUMN:
						return "" + ((EditableTableItem) element).getValue();
					}
					
					return "";
				}

				public void addListener(ILabelProviderListener listener) {
				}

				public void dispose() {
				}

				public boolean isLabelProperty(Object element, String property) {
					return false;
				}

				public void removeListener(ILabelProviderListener listener) {
				}
		    });
		    	
		  }
	
	public void refresh() {
		optionTable.removeAll();

		Iterator<String> iter = model.getSpaceOptions();

		while (iter.hasNext()) {
			final String name = iter.next();
			EditableTableItem item = new EditableTableItem(model, name, model.getSpaceOption(name));
			tableViewer.add(item);
		}

		for (TableColumn c : optionTable.getColumns()) {
			c.pack();
		}
	}

	public void setDirty(boolean b) {
		if (dirty != b) {
		  dirty = b;
		  firePropertyChange(PROP_DIRTY);
		}
	}
	
	public void newOption() {
		model.setSpaceOption("$exampleKey", 1);
		EditableTableItem item = new EditableTableItem(model, "$exampleKey", 1);
		tableViewer.add(item);
		setDirty(true);
	}
	
	public void deleteOption() {
		if (activeOption != null) {
			model.removeSpaceOption(activeOption.getText(NAME_COLUMN));
			optionTable.remove(optionTable.indexOf(activeOption));
			activeOption = null;
			setDirty(true);
		}
	}

	public boolean isDirty() {
		return dirty;
	}
	
	private class EditableTableItem {
		private String name;
		private Integer value;
		private Specification model;

		public EditableTableItem(Specification m, String n, Integer v) {
			this.model = m;
			name = n;
			value = v;
		}

		public String getName() {
			return name;
		}

		public Integer getValue() {
			return value;
		}

		public void setName(String name) {
			Integer value = model.getSpaceOption(this.name);
			model.removeSpaceOption(this.name);
			model.setSpaceOption(name, value);
			this.name = name;
		}

		public void setValue(Integer value) {
			model.setSpaceOption(name, value);
			this.value = value;
		}
	}
}
