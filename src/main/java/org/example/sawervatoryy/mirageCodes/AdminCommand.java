package org.example.sawervatoryy.mirageCodes;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "help", "create", "delete", "list", "info", "toggle",
            "setuses", "setperplayer", "setexpire", "setstart", "setperm", "setcase", "setmessage",
            "addcmd", "additem", "addmsg", "addbroadcast", "rmreward",
            "reset", "generate", "reload");

    private static final List<String> NEEDS_CODE = List.of(
            "delete", "info", "toggle", "setuses", "setperplayer", "setexpire", "setstart", "setperm",
            "setcase", "setmessage", "addcmd", "additem", "addmsg", "addbroadcast", "rmreward", "reset", "generate");

    private static final Pattern PREFIX = Pattern.compile("[\\p{L}\\p{N}_]{1,16}");

    private final MirageCodes plugin;

    public AdminCommand(MirageCodes plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    private CodeManager mgr() {
        return plugin.manager();
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (!s.hasPermission("miragecodes.admin")) {
            m().send(s, "no-permission");
            return true;
        }
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s, label);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(s, a);
            case "delete" -> delete(s, a);
            case "list" -> list(s, a);
            case "info" -> info(s, a);
            case "toggle" -> toggle(s, a);
            case "setuses" -> setLimit(s, a, true);
            case "setperplayer" -> setLimit(s, a, false);
            case "setexpire" -> setDate(s, a, false);
            case "setstart" -> setDate(s, a, true);
            case "setperm" -> setPerm(s, a);
            case "setcase" -> setCase(s, a);
            case "setmessage" -> setMessage(s, a);
            case "addcmd" -> addCmd(s, a);
            case "additem" -> addItem(s, a);
            case "addmsg" -> addText(s, a, "message");
            case "addbroadcast" -> addText(s, a, "broadcast");
            case "rmreward" -> rmReward(s, a);
            case "reset" -> reset(s, a);
            case "generate" -> generate(s, a);
            case "reload" -> {
                plugin.reloadAll();
                m().send(s, "admin.reloaded", "%count%", String.valueOf(mgr().all().size()));
            }
            default -> help(s, label);
        }
        return true;
    }

    private void usage(CommandSender s, String text) {
        m().send(s, "admin.usage", "%usage%", text);
    }

    private Code need(CommandSender s, String[] a, int minArgs, String usage) {
        if (a.length < minArgs) {
            usage(s, usage);
            return null;
        }
        Code code = mgr().byId(a[1]);
        if (code == null) m().send(s, "admin.not-found", "%code%", a[1]);
        return code;
    }

    private static String limit(int n) {
        return n < 0 ? "∞" : String.valueOf(n);
    }

    private static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private void help(CommandSender s, String label) {
        for (String line : m().list("admin.help")) m().sendRaw(s, line, "%label%", label);
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/mcodes create <код>");
            return;
        }
        if (!CodeManager.isValidId(a[1])) {
            m().send(s, "admin.invalid-id");
            return;
        }
        if (mgr().byId(a[1]) != null) {
            m().send(s, "admin.exists", "%code%", a[1]);
            return;
        }
        mgr().create(a[1]);
        m().send(s, "admin.created", "%code%", a[1]);
    }

    private void delete(CommandSender s, String[] a) {
        Code code = need(s, a, 2, "/mcodes delete <код>");
        if (code == null) return;
        mgr().delete(code);
        m().send(s, "admin.deleted", "%code%", code.getId());
    }

    private void list(CommandSender s, String[] a) {
        List<Code> all = new ArrayList<>(mgr().all());
        if (all.isEmpty()) {
            m().send(s, "admin.list-empty");
            return;
        }
        int page = 1;
        if (a.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(a[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        int per = 10;
        int pages = (all.size() + per - 1) / per;
        page = Math.min(page, pages);
        m().sendRaw(s, m().raw("admin.list-header"),
                "%page%", String.valueOf(page), "%pages%", String.valueOf(pages), "%total%", String.valueOf(all.size()));
        for (int i = (page - 1) * per; i < Math.min(all.size(), page * per); i++) {
            Code c = all.get(i);
            m().sendRaw(s, m().raw("admin.list-entry"),
                    "%code%", c.getId(),
                    "%status%", c.isEnabled() ? "&fвкл" : "&cвыкл",
                    "%uses%", String.valueOf(mgr().totalUses(c)),
                    "%max%", limit(c.getMaxUses()));
        }
    }

    private void info(CommandSender s, String[] a) {
        Code c = need(s, a, 2, "/mcodes info <код>");
        if (c == null) return;
        for (String line : m().list("admin.info")) {
            m().sendRaw(s, line,
                    "%code%", c.getId(),
                    "%enabled%", c.isEnabled() ? "включён" : "отключён",
                    "%case%", c.isCaseSensitive() ? "да" : "нет",
                    "%uses%", String.valueOf(mgr().totalUses(c)),
                    "%max%", limit(c.getMaxUses()),
                    "%per_player%", limit(c.getMaxUsesPerPlayer()),
                    "%start%", c.getStart() > 0 ? mgr().formatDate(c.getStart()) : "—",
                    "%expires%", c.getExpires() > 0 ? mgr().formatDate(c.getExpires()) : "никогда",
                    "%permission%", c.getPermission().isEmpty() ? "—" : c.getPermission(),
                    "%message%", c.getSuccessMessage().isEmpty() ? "—" : c.getSuccessMessage(),
                    "%rewards%", String.valueOf(c.getRewards().size()));
        }
        int i = 1;
        for (Reward r : c.getRewards()) {
            m().sendRaw(s, m().raw("admin.info-reward"),
                    "%index%", String.valueOf(i++), "%reward%", Messages.strip(r.describe()));
        }
    }

    private void toggle(CommandSender s, String[] a) {
        Code c = need(s, a, 2, "/mcodes toggle <код>");
        if (c == null) return;
        c.setEnabled(!c.isEnabled());
        mgr().saveNow();
        m().send(s, "admin.toggled", "%code%", c.getId(), "%state%", c.isEnabled() ? "включён" : "отключён");
    }

    private void setLimit(CommandSender s, String[] a, boolean global) {
        String name = global ? "setuses" : "setperplayer";
        Code c = need(s, a, 3, "/mcodes " + name + " <код> <число|unlimited>");
        if (c == null) return;
        String v = a[2].toLowerCase(Locale.ROOT);
        int n;
        if (v.equals("unlimited") || v.equals("inf") || v.equals("безлимит") || v.equals("-1")) {
            n = -1;
        } else {
            try {
                n = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                m().send(s, "admin.bad-number");
                return;
            }
            if (n < 0) {
                m().send(s, "admin.bad-number");
                return;
            }
        }
        if (global) c.setMaxUses(n);
        else c.setMaxUsesPerPlayer(n);
        mgr().saveNow();
        m().send(s, global ? "admin.set-uses" : "admin.set-per-player", "%code%", c.getId(), "%value%", limit(n));
    }

    private void setDate(CommandSender s, String[] a, boolean start) {
        String name = start ? "setstart" : "setexpire";
        Code c = need(s, a, 3, "/mcodes " + name + " <код> <дата|1h|7d|never>");
        if (c == null) return;
        long t;
        try {
            t = TimeUtil.parse(join(a, 2), mgr().zone(), true);
        } catch (IllegalArgumentException e) {
            m().send(s, "admin.bad-date");
            return;
        }
        if (start) c.setStart(t);
        else c.setExpires(t);
        mgr().saveNow();
        String shown = t > 0 ? mgr().formatDate(t) : (start ? "сразу" : "никогда");
        m().send(s, start ? "admin.set-start" : "admin.set-expire", "%code%", c.getId(), "%value%", shown);
    }

    private void setPerm(CommandSender s, String[] a) {
        Code c = need(s, a, 3, "/mcodes setperm <код> <право|none>");
        if (c == null) return;
        String v = a[2];
        boolean none = v.equalsIgnoreCase("none") || v.equals("-");
        c.setPermission(none ? "" : v);
        mgr().saveNow();
        m().send(s, "admin.set-perm", "%code%", c.getId(), "%value%", none ? "—" : v);
    }

    private void setCase(CommandSender s, String[] a) {
        Code c = need(s, a, 3, "/mcodes setcase <код> <true|false>");
        if (c == null) return;
        String v = a[2].toLowerCase(Locale.ROOT);
        if (!v.equals("true") && !v.equals("false")) {
            usage(s, "/mcodes setcase <код> <true|false>");
            return;
        }
        c.setCaseSensitive(Boolean.parseBoolean(v));
        mgr().saveNow();
        m().send(s, "admin.set-case", "%code%", c.getId(), "%value%", v.equals("true") ? "да" : "нет");
    }

    private void setMessage(CommandSender s, String[] a) {
        Code c = need(s, a, 3, "/mcodes setmessage <код> <текст|none>");
        if (c == null) return;
        String text = join(a, 2);
        boolean none = text.equalsIgnoreCase("none");
        c.setSuccessMessage(none ? "" : text);
        mgr().saveNow();
        m().send(s, "admin.set-message", "%code%", c.getId());
    }

    private void addReward(CommandSender s, Code c, Map<String, Object> map) {
        Reward r = new Reward(map);
        c.getRewards().add(r);
        mgr().saveNow();
        m().send(s, "admin.reward-added", "%code%", c.getId(), "%reward%", Messages.strip(r.describe()));
    }

    private void addCmd(CommandSender s, String[] a) {
        String u = "/mcodes addcmd <код> [console|player] <команда>";
        Code c = need(s, a, 3, u);
        if (c == null) return;
        int from = 2;
        String as = "console";
        if (a[2].equalsIgnoreCase("console") || a[2].equalsIgnoreCase("player")) {
            as = a[2].toLowerCase(Locale.ROOT);
            from = 3;
        }
        if (a.length <= from) {
            usage(s, u);
            return;
        }
        String cmd = join(a, from);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "command");
        map.put("as", as);
        map.put("command", cmd);
        addReward(s, c, map);
    }

    private void addItem(CommandSender s, String[] a) {
        Code c = need(s, a, 2, "/mcodes additem <код> [МАТЕРИАЛ] [количество]");
        if (c == null) return;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "item");
        if (a.length >= 3) {
            Material mat = Material.matchMaterial(a[2]);
            if (mat == null || mat.isAir() || !mat.isItem()) {
                m().send(s, "admin.bad-material", "%value%", a[2]);
                return;
            }
            int amount = 1;
            if (a.length >= 4) {
                try {
                    amount = Math.max(1, Math.min(Integer.parseInt(a[3]), 2304));
                } catch (NumberFormatException e) {
                    m().send(s, "admin.bad-number");
                    return;
                }
            }
            map.put("material", mat.name());
            map.put("amount", amount);
        } else {
            if (!(s instanceof Player p)) {
                m().send(s, "players-only");
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                m().send(s, "admin.hold-item");
                return;
            }
            map.put("item-data", Base64.getEncoder().encodeToString(hand.serializeAsBytes()));
        }
        addReward(s, c, map);
    }

    private void addText(CommandSender s, String[] a, String type) {
        String name = type.equals("message") ? "addmsg" : "addbroadcast";
        Code c = need(s, a, 3, "/mcodes " + name + " <код> <текст>");
        if (c == null) return;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("text", join(a, 2));
        addReward(s, c, map);
    }

    private void rmReward(CommandSender s, String[] a) {
        Code c = need(s, a, 3, "/mcodes rmreward <код> <номер>");
        if (c == null) return;
        int idx;
        try {
            idx = Integer.parseInt(a[2]);
        } catch (NumberFormatException e) {
            m().send(s, "admin.bad-number");
            return;
        }
        if (idx < 1 || idx > c.getRewards().size()) {
            m().send(s, "admin.bad-index");
            return;
        }
        c.getRewards().remove(idx - 1);
        mgr().saveNow();
        m().send(s, "admin.reward-removed", "%code%", c.getId(), "%index%", String.valueOf(idx));
    }

    private void reset(CommandSender s, String[] a) {
        Code c = need(s, a, 2, "/mcodes reset <код> [игрок]");
        if (c == null) return;
        if (a.length >= 3) {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(a[2]);
            if (op == null) {
                m().send(s, "admin.player-not-found", "%player%", a[2]);
                return;
            }
            mgr().resetUsage(c, op.getUniqueId());
            m().send(s, "admin.reset-player", "%code%", c.getId(), "%player%", a[2]);
        } else {
            mgr().resetUsage(c);
            m().send(s, "admin.reset-all", "%code%", c.getId());
        }
    }

    private void generate(CommandSender s, String[] a) {
        Code tpl = need(s, a, 3, "/mcodes generate <шаблон> <количество> [длина] [префикс]");
        if (tpl == null) return;
        int count;
        int length = 10;
        try {
            count = Integer.parseInt(a[2]);
            if (a.length >= 4) length = Integer.parseInt(a[3]);
        } catch (NumberFormatException e) {
            m().send(s, "admin.bad-number");
            return;
        }
        String prefix = a.length >= 5 ? a[4] : "";
        if (count < 1 || count > 1000 || length < 4 || length > 24
                || (!prefix.isEmpty() && !PREFIX.matcher(prefix).matches())
                || (prefix.isEmpty() ? 0 : prefix.length() + 1) + length > 32) {
            m().send(s, "admin.bad-generate");
            return;
        }
        CodeManager.Generated g = mgr().generate(tpl, count, length, prefix);
        m().send(s, "admin.generated",
                "%count%", String.valueOf(g.ids().size()), "%code%", tpl.getId(), "%file%", g.file());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        if (!s.hasPermission("miragecodes.admin")) return List.of();
        if (a.length == 1) return filter(SUBS, a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && NEEDS_CODE.contains(sub)) {
            List<String> ids = new ArrayList<>();
            Collection<Code> all = mgr().all();
            for (Code c : all) ids.add(c.getId());
            return filter(ids, a[1]);
        }
        if (a.length == 3) {
            return switch (sub) {
                case "setuses", "setperplayer" -> filter(List.of("unlimited", "1", "10", "100"), a[2]);
                case "setexpire", "setstart" -> filter(List.of("never", "1h", "1d", "7d", "30d"), a[2]);
                case "setcase" -> filter(List.of("true", "false"), a[2]);
                case "setperm" -> filter(List.of("none"), a[2]);
                case "setmessage" -> filter(List.of("none"), a[2]);
                case "addcmd" -> filter(List.of("console", "player"), a[2]);
                case "generate" -> filter(List.of("1", "10", "50", "100"), a[2]);
                case "reset" -> {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                    yield filter(names, a[2]);
                }
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String low = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
        }
        return out;
    }
}
