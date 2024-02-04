package com.mmiddletonn;

import org.bukkit.plugin.java.JavaPlugin;

public class SimpleAFK extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("SimpleAFK has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("SimpleAFK has been disabled.");
    }
}