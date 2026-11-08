package org.example.sawervatoryy.mirageCodes;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class Reward {

    private final Map<String, Object> data = new LinkedHashMap<>();

    public Reward(Map<?, ?> raw) {
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            data.put(String.valueOf(e.getKey()), e.getValue());
        }
    }

    public Reward copy() {
        return new Reward(data);
    }

    public Map<String, Object> asMap() {
        return data;
    }

    public String type() {
        return str("type", "command").toLowerCase(Locale.ROOT);
    }

    public String str(String key, String def) {
        Object o = data.get(key);
        return o == null ? def : String.valueOf(o);
    }

    public double num(String key, double def) {
        Object o = data.get(key);
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try {
                return Double.parseDouble(o.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public int integer(String key, int def) {
        return (int) num(key, def);
    }

    public boolean bool(String key, boolean def) {
        Object o = data.get(key);
        if (o instanceof Boolean b) return b;
        if (o != null) return Boolean.parseBoolean(o.toString().trim());
        return def;
    }

    public List<String> list(String key) {
        List<String> result = new ArrayList<>();
        Object o = data.get(key);
        if (o instanceof List<?> l) {
            for (Object x : l) result.add(String.valueOf(x));
        } else if (o != null) {
            result.add(String.valueOf(o));
        }
        return result;
    }

    public String validate() {
        return switch (type()) {
            case "command" -> str("command", "").isBlank() ? "пустая команда" : null;
            case "item" -> buildItem(s -> s) == null ? "неверный предмет (material / item-data)" : null;
            case "message", "broadcast" -> str("text", "").isBlank() ? "пустой текст" : null;
            case "title" -> (str("title", "").isBlank() && str("subtitle", "").isBlank()) ? "пустой титр" : null;
            case "sound" -> str("sound", "").isBlank() ? "не указан звук" : null;
            default -> "неизвестный тип '" + type() + "'";
        };
    }

    public ItemStack buildItem(UnaryOperator<String> filler) {
        String blob = str("item-data", "");
        if (!blob.isEmpty()) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(blob));
            } catch (Exception e) {
                return null;
            }
        }
        Material mat = Material.matchMaterial(str("material", ""));
        if (mat == null || mat.isAir() || !mat.isItem()) return null;
        int amount = Math.max(1, Math.min(integer("amount", 1), 2304));
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = str("name", "");
        if (!name.isEmpty()) meta.displayName(Messages.item(filler.apply(name)));

        List<String> lore = list("lore");
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(Messages.item(filler.apply(line)));
            meta.lore(lines);
        }

        for (String entry : list("enchantments")) {
            String key = entry.trim();
            int level = 1;
            int idx = key.lastIndexOf(':');
            if (idx > 0) {
                try {
                    level = Integer.parseInt(key.substring(idx + 1).trim());
                    key = key.substring(0, idx);
                } catch (NumberFormatException ignored) {
                }
            }
            NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
            Enchantment ench = nk == null ? null : Registry.ENCHANTMENT.get(nk);
            if (ench == null) continue;
            if (meta instanceof EnchantmentStorageMeta esm) {
                esm.addStoredEnchant(ench, level, true);
            } else {
                meta.addEnchant(ench, level, true);
            }
        }

        if (bool("unbreakable", false)) meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public String describe() {
        String base = switch (type()) {
            case "command" -> "[" + str("as", "console") + "] /" + str("command", "");
            case "item" -> describeItem();
            case "message" -> "сообщение: " + str("text", "");
            case "broadcast" -> "объявление: " + str("text", "");
            case "title" -> "титр: " + str("title", "") + " | " + str("subtitle", "");
            case "sound" -> "звук: " + str("sound", "");
            default -> "неизвестный тип: " + type();
        };
        StringBuilder sb = new StringBuilder(base);
        double chance = num("chance", 100);
        if (chance < 100) {
            sb.append(" (шанс ").append(chance == Math.floor(chance) ? String.valueOf((int) chance) : String.valueOf(chance)).append("%)");
        }
        int delay = integer("delay", 0);
        if (delay > 0) sb.append(" (через ").append(delay).append(" тик.)");
        return sb.toString();
    }

    private String describeItem() {
        String blob = str("item-data", "");
        if (!blob.isEmpty()) {
            try {
                ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(blob));
                return "предмет: " + it.getType().name() + " x" + it.getAmount() + " (сохранён целиком)";
            } catch (Exception e) {
                return "предмет (повреждённые данные)";
            }
        }
        return "предмет: " + str("material", "?") + " x" + integer("amount", 1);
    }
}
