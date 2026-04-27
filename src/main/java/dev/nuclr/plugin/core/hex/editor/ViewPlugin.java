package dev.nuclr.plugin.core.hex.editor;

import dev.nuclr.platform.plugin.NuclrPluginRole;

public class ViewPlugin extends EditPlugin {

	private static final String PLUGIN_ID = "dev.nuclr.plugin.core.hex.viewer";
	private static final String PLUGIN_NAME = "Hex Viewer";

	@Override
	public NuclrPluginRole role() {
		return NuclrPluginRole.FullScreenViewer;
	}

	@Override
	public boolean isEditable() {
		return false;
	}

	@Override
	public String name() {
		return PLUGIN_NAME;
	}

	@Override
	public String id() {
		return PLUGIN_ID;
	}

}
