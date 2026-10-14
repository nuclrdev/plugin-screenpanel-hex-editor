package dev.nuclr.plugin.core.hex.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.exbin.auxiliary.binary_data.array.ByteArrayEditableData;
import org.exbin.bined.CodeType;
import org.exbin.bined.EditMode;
import org.exbin.bined.EditOperation;
import org.exbin.bined.basic.BasicBackgroundPaintMode;
import org.exbin.bined.basic.CodeAreaViewMode;
import org.exbin.bined.swing.basic.CodeArea;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.platform.plugin.FullscreenNuclrPlugin;
import dev.nuclr.platform.plugin.NuclrMenuResource;
import dev.nuclr.platform.plugin.NuclrPluginCallback;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrResource;

public class EditPlugin implements FullscreenNuclrPlugin, NuclrEventListener {

	private static final String CLOSE_FULLSCREEN_ACTION = "plugin.fullscreen.close";
	private static final String SAVE_ACTION = "plugin.hex.editor.save";
	private static final String TOGGLE_OPERATION_ACTION = "plugin.hex.editor.toggleOperation";
	private static final String TOGGLE_VIEW_ACTION = "plugin.hex.editor.toggleView";

	private final String uuid = UUID.randomUUID().toString();
	private final JPanel panel = new JPanel(new BorderLayout());
	private final CodeArea codeArea = new CodeArea();
	private final JScrollPane scrollPane = new JScrollPane();
	private final ByteArrayEditableData contentData = new ByteArrayEditableData();

	private NuclrPluginContext context;
	private NuclrResource currentResource;
	private boolean dirty;

	public EditPlugin() {
		codeArea.setContentData(contentData);
		codeArea.setCodeType(CodeType.HEXADECIMAL);
		codeArea.setViewMode(CodeAreaViewMode.DUAL);
		codeArea.setBackgroundPaintMode(BasicBackgroundPaintMode.STRIPED);
		codeArea.setShowMirrorCursor(true);
		codeArea.setMaxBytesPerRow(16);
		codeArea.setMinRowPositionLength(8);
		codeArea.setEditMode(isEditable() ? EditMode.EXPANDING : EditMode.READ_ONLY);
		codeArea.setEditOperation(EditOperation.OVERWRITE);
		scrollPane.setViewportView(codeArea);
		panel.add(scrollPane, BorderLayout.CENTER);
		registerActions();
        registerFullscreenCloseShortcut();
		applyUiTheme(null);
	}

	/**
	 * Whether the open resource can be written back.
	 *
	 * <p>Only a resource with a local file can be: the resource API offers a way to read one but
	 * not to write one, so anything remote is held read-only.
	 *
	 * @return {@code true} when edits can be saved
	 */
	protected boolean isEditable() {
		return currentResource == null || currentResource.getPath() != null;
	}

	@Override
	public boolean isMessageSupported(String type) {
		return SAVE_ACTION.equals(type)
				|| TOGGLE_OPERATION_ACTION.equals(type)
				|| TOGGLE_VIEW_ACTION.equals(type);
	}

	@Override
	public boolean onFocusGained() {
		return codeArea.requestFocusInWindow();
	}

	@Override
	public void onFocusLost() {
	}

	@Override
	public boolean isFocused() {
		return codeArea.isFocusOwner()
				|| scrollPane.isFocusOwner()
				|| panel.isFocusOwner();
	}

	/** The largest resource with no local file this plugin will open. */
	private static final long MAX_REMOTE_BYTES = 32L * 1024 * 1024;

	@Override
	public JComponent panel() {
		return panel;
	}

	/**
	 * Whether this plugin can open {@code resource}.
	 *
	 * <p>A resource with no local file — a bucket object, a remote listing entry — is opened
	 * read-only: its bytes are read through the resource itself, and {@link #isEditable()}
	 * withholds saving, because {@link NuclrResource} can read a resource but not write one back.
	 *
	 * @param resource the resource to open
	 * @return {@code true} when it can be shown here
	 */
	@Override
	public boolean supports(NuclrResource resource) {
		if (resource == null || resource.isFolder() || !resource.isReadable()) {
			return false;
		}
		Path path = resource.getPath();
		if (path == null) {
			// Reading one means a network fetch on the event dispatch thread, and the bytes are
			// held in memory once they arrive, so a remote resource is only opened up to a size
			// that keeps both bearable.
			if (resource.getLength() > MAX_REMOTE_BYTES) {
				return false;
			}
			// A length of 0 is also what a resource reports when its size is unknown, including
			// rows that are not files at all (an AI project, say), so it has to show it can be read.
			return resource.getLength() > 0 || canOpen(resource);
		}
		return Files.isRegularFile(path) && Files.isReadable(path);
	}

	/** Whether the resource's content can be opened at all; the default resource cannot. */
	private static boolean canOpen(NuclrResource resource) {
		try (var in = resource.openInputStream()) {
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public List<NuclrMenuResource> menuItems(NuclrResource resource) {
		if (!isEditable()) {
			return List.of(
					new NuclrMenuResource("Mode", "F2", TOGGLE_VIEW_ACTION),
					new NuclrMenuResource("Quit", "F3", CLOSE_FULLSCREEN_ACTION));
		}
		return List.of(
				new NuclrMenuResource("Mode", "F2", TOGGLE_VIEW_ACTION),
				new NuclrMenuResource("Quit", "F3", CLOSE_FULLSCREEN_ACTION),
				new NuclrMenuResource("Save", "F4", SAVE_ACTION),
				new NuclrMenuResource("Toggle Ovr", "F6", TOGGLE_OPERATION_ACTION));
	}

	@Override
	public void preinit(NuclrPluginContext context) {
		this.context = context;
		if (context != null && context.getEventBus() != null) {
			context.getEventBus().subscribe(this);
		}
		applyUiTheme(context != null ? context.getTheme() : null);
	}

	@Override
	public void unload() {
		if (context != null && context.getEventBus() != null) {
			context.getEventBus().unsubscribe(this);
		}
	}

	@Override
	public boolean openResource(NuclrResource resource, AtomicBoolean cancelled) {
		if (cancelled != null && cancelled.get()) {
			return false;
		}
		if (!supports(resource)) {
			return false;
		}
		currentResource = resource;
		try {
			// Read through the resource rather than its path, so an entry with no local file
			// opens on the same terms as one with.
			byte[] bytes;
			try (var in = resource.openInputStream()) {
				bytes = in.readAllBytes();
			}
			if (cancelled != null && cancelled.get()) {
				return false;
			}
			contentData.clear();
			contentData.insert(0, bytes);
			codeArea.setContentData(contentData);
			codeArea.setEditMode(isEditable() ? EditMode.EXPANDING : EditMode.READ_ONLY);
			codeArea.setEditOperation(EditOperation.OVERWRITE);
			codeArea.setActiveCaretPosition(0);
			codeArea.revealCursor();
            JPopupMenu popupMenu = new JPopupMenu() {
                @Override
                public void show(Component invoker, int x, int y) {
                    createCodeAreaPopupMenu().show(invoker, x, y);
                }
            };
            codeArea.setComponentPopupMenu(popupMenu);
			dirty = false;
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
    
    private JPopupMenu createCodeAreaPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        Action cutAction = new AbstractAction("Cut") {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.cut();
            }
        };
        cutAction.setEnabled(codeArea.hasSelection() && codeArea.canPaste());
        popupMenu.add(cutAction);
        Action copyAction = new AbstractAction("Copy") {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.copy();
            }
        };
        copyAction.setEnabled(codeArea.hasSelection());
        popupMenu.add(copyAction);
        Action pasteAction = new AbstractAction("Paste") {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.paste();
            }
        };
        pasteAction.setEnabled(codeArea.canPaste());
        popupMenu.add(pasteAction);
        Action deleteAction = new AbstractAction("Delete") {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.delete();
            }
        };
        deleteAction.setEnabled(codeArea.hasSelection());
        popupMenu.add(deleteAction);
        popupMenu.addSeparator();
        Action selectAllAction = new AbstractAction("Select All") {
            @Override
            public void actionPerformed(ActionEvent e) {
                codeArea.selectAll();
            }
        };
        popupMenu.add(selectAllAction);
        return popupMenu;
    }

	@Override
	public void closeResource() {
		currentResource = null;
		contentData.clear();
		codeArea.setContentData(contentData);
		dirty = false;
	}

	@Override
	public NuclrResource getCurrentResource() {
		return currentResource;
	}

	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {
		applyUiTheme(themeScheme);
	}

	@Override
	public String uuid() {
		return uuid;
	}

	private void registerActions() {
		bindAction("ESCAPE", CLOSE_FULLSCREEN_ACTION, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
                requestClose();
			}
		});
		bindAction("F3", CLOSE_FULLSCREEN_ACTION, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
                requestClose();
			}
		});
		bindAction("F2", TOGGLE_VIEW_ACTION, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleViewMode();
			}
		});
		bindAction("F4", SAVE_ACTION, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				saveQuietly();
			}
		});
		bindAction("ctrl S", SAVE_ACTION, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				saveQuietly();
			}
		});
		bindAction("F6", TOGGLE_OPERATION_ACTION, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleEditOperation();
			}
		});
		codeArea.addDataChangedListener(() -> dirty = true);
	}

	private static void bindFullscreenClose(JComponent component, KeyStroke keyStroke, AbstractAction action) {
		bindAction(component, keyStroke, CLOSE_FULLSCREEN_ACTION, action);
	}

    private static void bindAction(JComponent component, KeyStroke keyStroke, String actionKey, AbstractAction action) {
		component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, actionKey);
		component.getActionMap().put(actionKey, action);
	}

    private void bindAction(String keyStroke, String actionKey, AbstractAction action) {
		KeyStroke stroke = KeyStroke.getKeyStroke(keyStroke);
        bindAction(codeArea, stroke, actionKey, action);
    }
    
	private void toggleViewMode() {
		codeArea.setViewMode(
				codeArea.getViewMode() == CodeAreaViewMode.DUAL
						? CodeAreaViewMode.CODE_MATRIX
						: CodeAreaViewMode.DUAL);
	}

	private void toggleEditOperation() {
		if (!isEditable() || codeArea.getEditMode() == EditMode.READ_ONLY) {
			return;
		}
		codeArea.setEditOperation(
				codeArea.getEditOperation() == EditOperation.OVERWRITE
						? EditOperation.INSERT
						: EditOperation.OVERWRITE);
	}

	private void saveQuietly() {
		if (!isEditable() || currentResource == null || currentResource.getPath() == null) {
			return;
		}
		try (var out = Files.newOutputStream(currentResource.getPath())) {
			contentData.saveToStream(out);
			dirty = false;
		} catch (Exception ignored) {
		}
	}

	private void applyUiTheme(NuclrThemeScheme themeScheme) {
		Font base = themeScheme != null ? themeScheme.defaultFont() : UIManager.getFont("defaultFont");
		if (base == null) {
			base = new Font(Font.MONOSPACED, Font.PLAIN, 12);
		}
		codeArea.setCodeFont(base.deriveFont(Font.PLAIN, base.getSize2D()));
		Color background = themeColor(themeScheme, "Panel.background", codeArea.getBackground());
		Color foreground = themeColor(themeScheme, "Panel.foreground", codeArea.getForeground());
		Color selectionBackground = themeColor(themeScheme, "Table.selectionBackground", codeArea.getSelectionColor());
		Color selectionForeground = themeColor(themeScheme, "Table.selectionForeground", codeArea.getSelectedTextColor());
		codeArea.setBackground(background);
		codeArea.setForeground(foreground);
		codeArea.setSelectionColor(selectionBackground);
		codeArea.setSelectedTextColor(selectionForeground);
		panel.setBackground(background);
		scrollPane.setBackground(background);
		scrollPane.getViewport().setBackground(background);
	}

	private Color themeColor(NuclrThemeScheme themeScheme, String key, Color fallback) {
		if (themeScheme != null) {
			return themeScheme.color(key, fallback);
		}
		Color color = UIManager.getColor(key);
		return color != null ? color : fallback;
	}

	private void registerFullscreenCloseShortcut() {
		var closeAction = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				requestClose();
			}
		};
		var escape = KeyStroke.getKeyStroke("ESCAPE");
		var f3 = KeyStroke.getKeyStroke("F3");
		bindFullscreenClose(panel, escape, closeAction);
		bindFullscreenClose(codeArea, escape, closeAction);
		bindFullscreenClose(panel, f3, closeAction);
		bindFullscreenClose(codeArea, f3, closeAction);
	}

	/**
	 * Handles a close request (F3/Escape). When there are unsaved edits the user is
	 * offered Save / Don't Save / Cancel; with no changes the editor closes silently.
	 */
	private void requestClose() {
		if (dirty && codeArea.isEditable()) {
			int choice = showUnsavedChangesDialog();
			if (choice == JOptionPane.YES_OPTION) {
                saveQuietly();
			} else if (choice != JOptionPane.NO_OPTION) {
				// Cancel or dialog dismissed: stay in the editor.
				return;
			}
		}
		emitClose();
	}
    
    private int showUnsavedChangesDialog() {
		var result = new int[] { JOptionPane.CANCEL_OPTION };
		var save = new JButton("Save");
		var dontSave = new JButton("Don't Save");
		var cancel = new JButton("Cancel");
		var buttons = new JButton[] { save, dontSave, cancel };

		var pane = new JOptionPane("Save changes before closing?", JOptionPane.WARNING_MESSAGE,
				JOptionPane.YES_NO_CANCEL_OPTION, null, buttons, save);

		save.addActionListener(e -> {
			result[0] = JOptionPane.YES_OPTION;
			pane.setValue(save);
		});
		dontSave.addActionListener(e -> {
			result[0] = JOptionPane.NO_OPTION;
			pane.setValue(dontSave);
		});
		cancel.addActionListener(e -> {
			result[0] = JOptionPane.CANCEL_OPTION;
			pane.setValue(cancel);
		});

		JDialog dialog = pane.createDialog(panel, "Unsaved changes");
		installDialogButtonTraversal(dialog, buttons);
		SwingUtilities.invokeLater(() -> focusDialogButton(save));
		dialog.setVisible(true);
		dialog.dispose();

		return result[0];
	}

	private static void installDialogButtonTraversal(JDialog dialog, JButton[] buttons) {
		for (int i = 0; i < buttons.length; i++) {
			JButton button = buttons[i];
			int previous = (i + buttons.length - 1) % buttons.length;
			int next = (i + 1) % buttons.length;

			bindFocusMove(button, "LEFT", buttons[previous]);
			bindFocusMove(button, "UP", buttons[previous]);
			bindFocusMove(button, "RIGHT", buttons[next]);
			bindFocusMove(button, "DOWN", buttons[next]);
			button.addFocusListener(new FocusAdapter() {
				@Override
				public void focusGained(FocusEvent e) {
					button.getRootPane().setDefaultButton(button);
				}
			});
		}

		dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"),
				"cancel");
		dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				buttons[buttons.length - 1].doClick();
			}
		});
	}

	private static void bindFocusMove(JButton source, String keyStroke, JButton target) {
		String actionKey = "focus." + keyStroke.toLowerCase();
		source.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyStroke), actionKey);
		source.getActionMap().put(actionKey, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				focusDialogButton(target);
			}
		});
	}

	private static void focusDialogButton(JButton button) {
		button.requestFocusInWindow();
		if (button.getRootPane() != null) {
			button.getRootPane().setDefaultButton(button);
		}
	}

	private void emitClose() {
		if (context != null && context.getEventBus() != null) {
			context.getEventBus().emit(CLOSE_FULLSCREEN_ACTION);
		}
	}

	@Override
	public NuclrPluginContext getContext() {
		return context;
	}

	@Override
	public void init() {
		
	}

	@Override
	public void handleMessage(Object source, String type, Map<String, Object> eventData, NuclrPluginCallback callback) {
		
		if (SAVE_ACTION.equals(type) && isFocused()) {
			saveQuietly();
			return;
		}
		
		if (TOGGLE_OPERATION_ACTION.equals(type) && isFocused() && isEditable()) {
			toggleEditOperation();
			return;
		}
		
		if (TOGGLE_VIEW_ACTION.equals(type) && isFocused()) {
			toggleViewMode();
		}
		
	}


}
