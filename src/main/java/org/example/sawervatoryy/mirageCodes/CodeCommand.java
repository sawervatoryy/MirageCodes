package org.example.sawervatoryy.mirageCodes;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class CodeCommand implements CommandExecutor, TabCompleter {

    private final MirageCodes plugin;

    public CodeCommand(MirageCodes plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("miragecodes.use")) {
            m.send(p, "no-permission");
            return true;
        }
        if (args.length < 1) {
            m.send(p, "usage", "%label%", label);
            return true;
        }
        plugin.manager().redeem(p, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
