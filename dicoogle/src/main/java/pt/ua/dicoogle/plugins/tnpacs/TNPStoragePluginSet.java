package pt.ua.dicoogle.plugins.tnpacs;

import pt.ua.dicoogle.sdk.PluginBase;

public class TNPStoragePluginSet extends PluginBase {
    private static final String name = "tnpacs-storage";

    public TNPStoragePluginSet() {
        TNPStoragePlugin storagePlugin = new TNPStoragePlugin();
        storagePlugin.enable();
        super.storagePlugins.add(storagePlugin);
    }

    @Override
    public String getName() {
        return name;
    }
}
