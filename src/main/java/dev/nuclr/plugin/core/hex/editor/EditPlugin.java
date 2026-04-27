package dev.nuclr.plugin.core.hex.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import org.exbin.auxiliary.binary_data.array.ByteArrayEditableData;
import org.exbin.bined.CodeType;
import org.exbin.bined.EditMode;
import org.exbin.bined.EditOperation;
import org.exbin.bined.basic.BasicBackgroundPaintMode;
import org.exbin.bined.basic.CodeAreaViewMode;
import org.exbin.bined.swing.basic.CodeArea;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.platform.plugin.NuclrMenuResource;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import dev.nuclr.platform.plugin.NuclrResourcePath;

public class EditPlugin implements NuclrPlugin, NuclrEventListener {

	private static final String PLUGIN_ID = "dev.nuclr.plugin.core.hex.editor";
	private static final String PLUGIN_NAME = "Hex Editor";
	private static final String PLUGIN_VERSION = "1.0.0";
	private static final String PLUGIN_DESCRIPTION = "Fullscreen hexadecimal viewer/editor.";
	private static final String PLUGIN_AUTHOR = "Nuclr Development Team";
	private static final String PLUGIN_LICENSE = "Apache-2.0";
	private static final String PLUGIN_WEBSITE = "https://nuclr.dev";
	private static final String PLUGIN_PAGE_URL = "https://nuclr.dev/plugins/core/screenpanel-hex-editor.html";
	private static final String PLUGIN_DOC_URL = PLUGIN_PAGE_URL;
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
	private NuclrResourcePath currentResource;
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
		applyUiTheme(null);
	}

	protected boolean isEditable() {
		return true;
	}

	@Override
	public void handleMessage(Object source, String type, Map<String, Object> event) {
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

	@Override
	public String id() {
		return PLUGIN_ID;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public String version() {
		return PLUGIN_VERSION;
	}

	@Override
	public String description() {
		return PLUGIN_DESCRIPTION;
	}

	@Override
	public String author() {
		return PLUGIN_AUTHOR;
	}

	@Override
	public String license() {
		return PLUGIN_LICENSE;
	}

	@Override
	public String website() {
		return PLUGIN_WEBSITE;
	}

	@Override
	public String pageUrl() {
		return PLUGIN_PAGE_URL;
	}

	@Override
	public String docUrl() {
		return PLUGIN_DOC_URL;
	}

	@Override
	public Developer type() {
		return Developer.Official;
	}

	@Override
	public JComponent panel() {
		return panel;
	}

	@Override
	public boolean supports(NuclrResourcePath resource) {
		return resource != null
				&& resource.getPath() != null
				&& Files.isRegularFile(resource.getPath())
				&& Files.isReadable(resource.getPath());
	}

	@Override
	public NuclrPluginRole role() {
		return NuclrPluginRole.FullScreenEditor;
	}

	@Override
	public List<NuclrMenuResource> menuItems(NuclrResourcePath resource) {
		if (!isEditable()) {
			return List.of(
					new MenuResource("Mode", "F2", TOGGLE_VIEW_ACTION),
					new MenuResource("Quit", "F3", CLOSE_FULLSCREEN_ACTION));
		}
		return List.of(
				new MenuResource("Mode", "F2", TOGGLE_VIEW_ACTION),
				new MenuResource("Quit", "F3", CLOSE_FULLSCREEN_ACTION),
				new MenuResource("Save", "F4", SAVE_ACTION),
				new MenuResource("Toggle Ovr", "F6", TOGGLE_OPERATION_ACTION));
	}

	@Override
	public void load(NuclrPluginContext context, boolean isTemplate) {
		this.context = context;
		if (!isTemplate && context != null && context.getEventBus() != null) {
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
	public boolean openResource(NuclrResourcePath resource, AtomicBoolean cancelled) {
		if (cancelled != null && cancelled.get()) {
			return false;
		}
		if (!supports(resource)) {
			return false;
		}
		try {
			byte[] bytes = Files.readAllBytes(resource.getPath());
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
			currentResource = resource;
			dirty = false;
			return true;
		} catch (Exception ex) {
			currentResource = resource;
			return false;
		}
	}

	@Override
	public void closeResource() {
		currentResource = null;
		contentData.clear();
		codeArea.setContentData(contentData);
		dirty = false;
	}

	@Override
	public NuclrResourcePath getCurrentResource() {
		return currentResource;
	}

	@Override
	public int priority() {
		return 100;
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
				if (context != null && context.getEventBus() != null) {
					context.getEventBus().emit(CLOSE_FULLSCREEN_ACTION);
				}
			}
		});
		bindAction("F3", CLOSE_FULLSCREEN_ACTION, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (context != null && context.getEventBus() != null) {
					context.getEventBus().emit(CLOSE_FULLSCREEN_ACTION);
				}
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

	private void bindAction(String keyStroke, String actionKey, AbstractAction action) {
		KeyStroke stroke = KeyStroke.getKeyStroke(keyStroke);
		codeArea.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, actionKey);
		codeArea.getActionMap().put(actionKey, action);
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
}
