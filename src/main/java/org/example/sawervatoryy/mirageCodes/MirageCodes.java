package org.example.sawervatoryy.mirageCodes;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MirageCodes extends JavaPlugin {

    private Messages messages;
    private CodeManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "codes.yml").exists()) {
            saveResource("codes.yml", false);
        }

        messages = new Messages(this);
        manager = new CodeManager(this);
        manager.load();
        manager.startAutoSave();

        CodeCommand codeCommand = new CodeCommand(this);
        PluginCommand code = getCommand("code");
        if (code != null) {
            code.setExecutor(codeCommand);
            code.setTabCompleter(codeCommand);
        }

        AdminCommand adminCommand = new AdminCommand(this);
        PluginCommand admin = getCommand("miragecodes");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }

        getLogger().info("MirageCodes включён.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        manager.load();
    }

    public Messages messages() {
        return messages;
    }

    public CodeManager manager() {
        return manager;
    }
}
