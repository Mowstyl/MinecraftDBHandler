package com.clanjhoo.dbhandler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public final class DBHandler extends JavaPlugin {

    private static DBHandler instance = null;
    private File storage = null;
    private boolean working = false;

    @Override
    public void onLoad() {
        // Plugin startup logic
        try {
            File data = instance.getDataFolder();
            storage = new File(data, "storage");
            instance = this;
            working = true;
        }
        catch (Exception ex) {
            log(Level.SEVERE, "Error while initializing the plugin! Disabling...");
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onEnable() {
        // Plugin startup logic

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        working = false;
    }

    public static DBHandler getInstance() {
        return instance;
    }

    public static File getStorageFolder() {
        File data = instance.getDataFolder();
        File storage = new File(data, "storage");
        return storage;
    }

    public static void log(Level level, String message) {
        instance.getLogger().log(level, message);
    }
}
