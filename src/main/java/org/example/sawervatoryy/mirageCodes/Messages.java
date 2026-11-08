package org.example.sawervatoryy.mirageCodes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class Messages {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final MirageCodes plugin;

    public Messages(MirageCodes plugin) {
        this.plugin = plugin;
    }

    public static Component color(String text) {
        return SERIALIZER.deserialize(text == null ? "" : text);
    }

    public static Component item(String text) {
        return color(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String apply(String text, String... pairs) {
        if (text == null) return "";
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace(pairs[i], pairs[i + 1]);
        }
        return text;
    }

    public static String strip(String text) {
        if (text == null) return "";
        return text.replaceAll("&#[0-9a-fA-F]{6}|&[0-9a-fk-orA-FK-OR]", "");
    }

    public String raw(String key) {
        String v = plugin.getConfig().getString("messages." + key);
        return v == null ? "&cОтсутствует сообщение: " + key : v;
    }

    public String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    public List<String> list(String key) {
        return plugin.getConfig().getStringList("messages." + key);
    }

    public void send(CommandSender to, String key, String... pairs) {
        String text = raw(key);
        if (text.isEmpty()) return;
        to.sendMessage(color(prefix() + apply(text, pairs)));
    }

    public void sendRaw(CommandSender to, String text, String... pairs) {
        to.sendMessage(color(apply(text, pairs)));
    }
}
