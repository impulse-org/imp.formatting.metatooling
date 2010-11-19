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

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.imp.box.builders.BoxException;
import org.eclipse.imp.box.builders.BoxFactory;
import org.eclipse.imp.formatting.spec.BoxStringBuilder;
import org.eclipse.imp.formatting.spec.Item;
import org.eclipse.imp.formatting.spec.Parser;
import org.eclipse.imp.formatting.spec.Rule;
import org.eclipse.imp.formatting.spec.Separator;
import org.eclipse.imp.formatting.spec.SpaceOptionBinder;
import org.eclipse.imp.formatting.spec.Specification;
import org.eclipse.imp.utils.SavingMessageHandler;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
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

public class RuleTable implements IEditorPart {
	private Table fRuleTable;
	private TableEditor fTableEditor;

	private final int STATUS_COLUMN = 0;
	private final int EDIT_COLUMN = 1;
	private final int PREVIEW_COLUMN = 2;

	private final int MARGIN = 2;

	private Item fActiveItem;

	private List<IPropertyListener> fListeners;

	private Specification fModel;
	private boolean fDirty = false;
	private IEditorSite fSite;
	private IEditorInput fInput;

	private Font fSeparatorCellFont;

	public RuleTable() {
		fListeners = new LinkedList<IPropertyListener>();
	}
	
	public void setModel(Specification model) {
		this.fModel = model;
		refresh();
	}
	
    public void addPropertyListener(IPropertyListener l) {
        fListeners.add(l);
    }
    
    private void firePropertyChange(int change) {
    	for (IPropertyListener l : fListeners) {
    		l.propertyChanged(this, change);
    	}
    }
    
    public IEditorInput getEditorInput() {
		return fInput;
	}

	public IEditorSite getEditorSite() {
		return fSite;
	}

	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		this.fSite = site;
		this.fInput = input;
	}

	public void dispose() {
		fRuleTable.dispose();
		fSeparatorCellFont.dispose();
	}

	public IWorkbenchPartSite getSite() {
		return fSite;
	}

	public String getTitle() {
		return "Rules";
	}

	public Image getTitleImage() {
		return null;
	}

	public String getTitleToolTip() {
		return null;
	}

	
	public void removePropertyListener(IPropertyListener listener) {
		fListeners.remove(listener);
	}

	public void setFocus() {
		fRuleTable.setFocus();
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
		parent.setLayout(new FillLayout(SWT.HORIZONTAL));

		fRuleTable = new Table(parent, SWT.FULL_SELECTION);
		fRuleTable.setLinesVisible(true);
		fRuleTable.setHeaderVisible(true);

		TableColumn status = new TableColumn(fRuleTable, SWT.BORDER);
		status.setText("Status");
		status.setResizable(true);

		TableColumn box = new TableColumn(fRuleTable, SWT.BORDER);
		box.setText("Box");
		box.setResizable(true);

		TableColumn preview = new TableColumn(fRuleTable, SWT.BORDER);
		preview.setText("Preview");
		preview.setResizable(true);

		status.pack();
		box.pack();
		preview.pack();

		createCellEditor();
		createCellPainter();
		createCellTooltip();

		fSeparatorCellFont= new Font(parent.getDisplay(), "Monospace", 14, SWT.BOLD);
	}
	
	private void createCellEditor() {
		fTableEditor = new TableEditor(fRuleTable);
		fTableEditor.horizontalAlignment = SWT.LEFT;
		fTableEditor.verticalAlignment = SWT.TOP;
		fTableEditor.grabHorizontal = true;
		fTableEditor.grabVertical = true;
		fTableEditor.minimumWidth = 50;

		fRuleTable.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				Control oldEditor = fTableEditor.getEditor();
				if (oldEditor != null) {
					oldEditor.dispose();
				}

				TableItem item = (TableItem) e.item;
				if (item == null)
					return;

				fActiveItem = (Item) item.getData();

				final Text newEditor = new Text(fRuleTable, SWT.MULTI | SWT.WRAP | SWT.BORDER);

				newEditor.setFont(item.getFont());
				newEditor.setText(item.getText(EDIT_COLUMN));

				if (fActiveItem instanceof Rule) {
					newEditor.addModifyListener(new RuleModifier());
				} else if (fActiveItem instanceof Separator) {
					newEditor.addModifyListener(new SeparatorModifier());
				}

				newEditor.addFocusListener(new FocusAdapter() {
					public void focusLost(FocusEvent e) {
						newEditor.dispose();
					}
				});

				newEditor.setFocus();
				fTableEditor.setEditor(newEditor, item, EDIT_COLUMN);
			}
		});
	}
	
	private void disposeTableEditor() {
		Control e = fTableEditor.getEditor();
		if (e != null) {
			e.dispose();
		}
	}

	private void createCellTooltip() {
		fRuleTable.setToolTipText("");

		Listener tooltipListener = new Listener() {
			Shell tip = null;
			Label label = null;
			Display display = fRuleTable.getDisplay();
			Shell shell = fRuleTable.getShell();

			public void handleEvent(Event event) {
				switch (event.type) {
				case SWT.Dispose:
				case SWT.KeyDown:
				case SWT.MouseMove: {
					if (tip == null)
						break;
					tip.dispose();
					tip = null;
					label = null;
					break;
				}
				case SWT.MouseHover: {
					TableItem item = fRuleTable.getItem(new Point(event.x, event.y));
					if (item != null) {
						if (tip != null && !tip.isDisposed()) {
							tip.dispose();
						}
						tip = new Shell(shell, SWT.ON_TOP | SWT.TOOL);
						tip.setLayout(new FillLayout());
						label = new Label(tip, SWT.NONE);
						label.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
						label.setBackground(fRuleTable.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
						String text = (String) item.getData("tooltip");

						if (text != null) {
							label.setText(text);

							Point size = tip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
							Rectangle rect = item.getBounds(0);
							Point pt = fRuleTable.toDisplay(rect.x, rect.y);
							tip.setBounds(pt.x + size.y, pt.y - size.y, size.x, size.y);
							tip.setVisible(true);
						}
					}
				}
				}
			}
		};

		fRuleTable.addListener(SWT.KeyDown, tooltipListener);
		fRuleTable.addListener(SWT.Dispose, tooltipListener);
		fRuleTable.addListener(SWT.MouseHover, tooltipListener);
		fRuleTable.addListener(SWT.MouseMove, tooltipListener);
	}

	private void createCellPainter() {
		fRuleTable.addListener(SWT.MeasureItem, new Listener() {
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				String text = item.getText(event.index);
				event.gc.setFont(item.getFont());
				Point size = event.gc.textExtent(text, SWT.DRAW_DELIMITER);
				event.width = size.x + 2 * MARGIN;
				event.height = size.y + MARGIN;
			}
		});
		fRuleTable.addListener(SWT.EraseItem, new Listener() {
			public void handleEvent(Event event) {
				event.detail &= ~SWT.FOREGROUND;
			}
		});
		fRuleTable.addListener(SWT.PaintItem, new Listener() {
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				String text = item.getText(event.index);
				event.gc.setFont(item.getFont());
				event.gc.drawText(text, event.x + MARGIN, event.y + MARGIN, true);
			}
		});
	}

	public void addSeparator() {
		Separator s = new Separator();

		if (fActiveItem != null) {
			disposeTableEditor();
			int i = fModel.getRules().indexOf(fActiveItem);
			fModel.addSeparator(i, s);
			TableItem item = new TableItem(fRuleTable, SWT.NONE, i);
			initSeparatorTableItem(s, item);
			fRuleTable.select(i);
			fActiveItem = s;
		} else {
			fModel.addRule(s);
			TableItem item = new TableItem(fRuleTable, SWT.NONE);
			initSeparatorTableItem(s, item);
			fRuleTable.select(fRuleTable.getChildren().length);
		}
	}

	private void initSeparatorTableItem(Separator s, TableItem item) {
		item.setData(s);
		updateSeparatorTableItem(item, s.getLabel());
	}

	private void updateSeparatorTableItem(TableItem item, String label) {
		item.setText(EDIT_COLUMN, label);
		item.setText(PREVIEW_COLUMN, "");
		item.setText(STATUS_COLUMN, "---");
		item.setFont(fSeparatorCellFont);
	}

	private final class RuleModifier implements ModifyListener {
		public void modifyText(ModifyEvent e) {
			Text text = (Text) fTableEditor.getEditor();
			String b = text.getText();
			TableItem i = fTableEditor.getItem();
			i.setText(EDIT_COLUMN, b);
			Rule rule = (Rule) fTableEditor.getItem().getData();

			rule.setBoxString(b);
			updateRuleTableItem(i, rule, true);
			setDirty(true);
		}
	}

	private final class SeparatorModifier implements ModifyListener {
		public void modifyText(ModifyEvent e) {
			Text text = (Text) fTableEditor.getEditor();
			String l = text.getText();
			TableItem i = fTableEditor.getItem();
			i.setText(EDIT_COLUMN, l);
			Separator sep = (Separator) fTableEditor.getItem().getData();

			sep.setLabel(l);
			updateSeparatorTableItem(i, l);
			setDirty(true);
		}
	}
	
	public void refresh() {
		fRuleTable.removeAll();

		Iterator<Item> iter = fModel.ruleIterator();

		while (iter.hasNext()) {
			final Item i = iter.next();
			TableItem item = new TableItem(fRuleTable, SWT.NONE);

			if (i instanceof Rule) {
				initRuleTableItem((Rule) i, item, false);
			} else if (i instanceof Separator) {
				initSeparatorTableItem((Separator) i, item);
			}
		}

		for (TableColumn c : fRuleTable.getColumns()) {
			c.pack();
		}
	}
	
	public void setDirty(boolean b) {
		if (fDirty != b) {
		  fDirty = b;
		  firePropertyChange(PROP_DIRTY);
		}
	}
	
	private void initRuleTableItem(final Rule rule, TableItem item, boolean recompute) {
		item.setData(rule);
		updateRuleTableItem(item, rule, recompute);
	}

	private void updateRuleTableItem(TableItem item, Rule rule, boolean recompute) {
		String boxString = rule.getBoxString();
		item.setText(EDIT_COLUMN, boxString == null ? "\n" : boxString);

		if (boxString != null) {
			Parser parser = fModel.getParser();

			parser.getMessageHandler().clearMessages();
			if (parser.parseBox(boxString) != null) {
				String formatted;
				if (recompute) {
					try {
						formatted = getFormattedBox(boxString);
					} catch (BoxException e) {
						setItemAttribs(item, e.getMessage(), e.getBoxString());
						return;
					}
				 
				 if (formatted != null && formatted.length() > 0) {
				   rule.setPatternString(formatted);
				 }
				} else {
				  formatted = rule.getPatternString();
				  if (formatted == null) {
					  formatted = "";
				  }
				}
				
				item.setText(PREVIEW_COLUMN, formatted);
				Object ast = parser.parseObject(formatted);

				if (ast == null) {
	                SavingMessageHandler smh= (SavingMessageHandler) parser.getMessageHandler();

	                if (smh.getMessages().size() == 1 && smh.getConcatenatedMessages().contains("Unable to parse formatted text:")) {
                        setItemAttribs(item, "Unable to parse formatted text: no parser", "");
	                } else {
                        setItemAttribs(item, "Syntax error in formatted output", smh.getConcatenatedMessages());
	                }
				} else {
					rule.setPatternAst(ast);
					setItemAttribs(item, "Ok", ast.getClass().getName());
				}
			} else {
			    SavingMessageHandler smh= (SavingMessageHandler) parser.getMessageHandler();

			    setItemAttribs(item, "Syntax error in box rule", smh.getConcatenatedMessages());
			}
		} else {
			setItemAttribs(item, "Empty box rule", "");
		}
	}

	private void setItemAttribs(TableItem item, String text, String tooltip) {
		item.setText(STATUS_COLUMN, text);
		item.setData("tooltip", tooltip);
	}

	private String getFormattedBox(String boxString) throws BoxException {
		if (boxString != null && boxString.length() > 0) {
			SpaceOptionBinder binder = new SpaceOptionBinder(fModel);
			String boundString = binder.bind(boxString);
			SavingMessageHandler smh= new SavingMessageHandler();
			String result= BoxFactory.box2Text(boundString, smh);

			if (smh.getMessages().size() > 0) {
			    throw new BoxException(smh.getConcatenatedMessages(), boxString, null);
			}
			return result;
		} else {
			return "";
		}
	}
	
	public void move(int diff) {
		if (fActiveItem != null && fActiveItem instanceof Rule) {
			Rule r = (Rule) fActiveItem;
			List rules = fModel.getRules();
			int cur = rules.indexOf(fActiveItem);
			
			if (cur + diff >= 0) {
				disposeTableEditor();
				
				fRuleTable.remove(cur);
				fModel.removeRule(fActiveItem);
				fModel.addRule(cur + diff, r);
				
				TableItem item = new TableItem(fRuleTable, SWT.NONE, cur + diff);
				initRuleTableItem(r, item, false);
				fRuleTable.select(cur + diff);
				
				setDirty(true);
			}
		}
	}
	
	public void deleteRule() {
		if (fActiveItem != null) {
			disposeTableEditor();
			int i = fModel.getRules().indexOf(fActiveItem);
			fRuleTable.deselectAll();
			fRuleTable.remove(i);
			fModel.removeRule(i);
			setDirty(true);
		}
	}
	
	public void newRule() {
		Rule r = new Rule();

		if (fActiveItem != null) {
			disposeTableEditor();
			int i = fModel.getRules().indexOf(fActiveItem);
			if (i < 0) { i = 0; }
			fModel.addRule(i, r);
			TableItem item = new TableItem(fRuleTable, SWT.NONE, i);
			initRuleTableItem(r, item, false);
			fRuleTable.select(i);
			fActiveItem = r;
			setDirty(true);
		} else {
			fModel.addRule(r);
			TableItem item = new TableItem(fRuleTable, SWT.NONE);
			initRuleTableItem(r, item, false);
			fRuleTable.select(fRuleTable.getChildren().length);
			setDirty(true);
		}
	}
	
	public void formatRule() {
		if (fActiveItem != null && fActiveItem instanceof Rule) {
			Rule rule = (Rule) fActiveItem;
			disposeTableEditor();
			String box = rule.getBoxString();
			String formatted;
			try {
				formatted = BoxFactory.formatBox(box);
				if (formatted != null) {
					rule.setBoxString(formatted);
					int i = fModel.getRules().indexOf(rule);
					fRuleTable.getItem(i).setText(EDIT_COLUMN, formatted);
					setDirty(true);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void addRuleFromExample() {
		disposeTableEditor();

		IInputValidator v = new IInputValidator() {
			public String isValid(String newText) {
				Parser parser = fModel.getParser();
				if (parser.parseObject(newText) != null) {
					return null;
				} else {
					return "Not a valid string";
				}
			}
		};

		InputDialog dialog = new InputDialog(fRuleTable.getShell(), "provide your example", "", null, v);

		dialog.setBlockOnOpen(true);
		dialog.open();

		if (dialog.getReturnCode() == Window.OK) {
			String result = dialog.getValue();
			if (result != null) {
				newRule();
				Rule rule = (Rule) fActiveItem;
				String box = BoxStringBuilder.exampleToBox(result);
				rule.setBoxString(box);
				int i = fModel.getRules().indexOf(fActiveItem);
				TableItem item = fRuleTable.getItem(i);
				updateRuleTableItem(item, rule, true);
				setDirty(true);
			}
		}
	}

	public boolean isDirty() {
		return fDirty;
	}
}
