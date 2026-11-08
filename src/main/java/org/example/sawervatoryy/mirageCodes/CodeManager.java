package org.example.sawervatoryy.mirageCodes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class CodeManager {

    private static final Pattern VALID_ID = Pattern.compile("[\\p{L}\\p{N}_-]{2,32}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public record Generated(List<String> ids, String file) {
    }

    private static final class Usage {
        int total;
        final Map<UUID, Integer> players = new HashMap<>();
    }

    private final MirageCodes plugin;
    private final File codesFile;
    private final File usageFile;
    private final File logFile;
    private final Object fileLock = new Object();

    private final Map<String, Code> codes = new LinkedHashMap<>();
    private final Map<String, Usage> usage = new HashMap<>();
    private final Map<UUID, Long> lastAttempt = new HashMap<>();
    private final Map<UUID, Integer> failed = new HashMap<>();
    private final Map<UUID, Long> lockedUntil = new HashMap<>();

    private boolean codesDirty;
    private boolean usageDirty;
    private BukkitTask autosave;

    public CodeManager(MirageCodes plugin) {
        this.plugin = plugin;
        this.codesFile = new File(plugin.getDataFolder(), "codes.yml");
        this.usageFile = new File(plugin.getDataFolder(), "usage.yml");
        this.logFile = new File(plugin.getDataFolder(), "redeems.log");
    }

    public static boolean isValidId(String id) {
        return id != null && VALID_ID.matcher(id).matches();
    }

    public void load() {
        flush(false);
        codes.clear();
        usage.clear();
        ZoneId zone = zone();
        boolean defCase = plugin.getConfig().getBoolean("settings.case-sensitive-default", false);
        List<String> warnings = new ArrayList<>();

        YamlConfiguration cy = YamlConfiguration.loadConfiguration(codesFile);
        ConfigurationSection root = cy.getConfigurationSection("codes");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) continue;
                if (!isValidId(key)) {
                    warnings.add("Промокод '" + key + "' пропущен: ID должен быть 2–32 символа (буквы, цифры, _ и -).");
                    continue;
                }
                String low = key.toLowerCase(Locale.ROOT);
                if (codes.containsKey(low)) {
                    warnings.add("Промокод '" + key + "' пропущен: дубликат (регистр не учитывается в ID).");
                    continue;
                }
                codes.put(low, Code.load(key, sec, zone, defCase, warnings));
            }
        }

        YamlConfiguration uy = YamlConfiguration.loadConfiguration(usageFile);
        ConfigurationSection ur = uy.getConfigurationSection("usage");
        if (ur != null) {
            for (String key : ur.getKeys(false)) {
                ConfigurationSection sec = ur.getConfigurationSection(key);
                if (sec == null) continue;
                Usage u = new Usage();
                u.total = sec.getInt("total", 0);
                ConfigurationSection pl = sec.getConfigurationSection("players");
                if (pl != null) {
                    for (String uid : pl.getKeys(false)) {
                        try {
                            u.players.put(UUID.fromString(uid), pl.getInt(uid));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                usage.put(key.toLowerCase(Locale.ROOT), u);
            }
        }

        for (String w : warnings) plugin.getLogger().warning(w);
        plugin.getLogger().info("Загружено промокодов: " + codes.size());
    }

    public void startAutoSave() {
        long ticks = Math.max(5L, plugin.getConfig().getLong("settings.autosave-seconds", 30L)) * 20L;
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            flush(true);
            purge();
        }, ticks, ticks);
    }

    public void shutdown() {
        if (autosave != null) autosave.cancel();
        flush(false);
    }

    public void saveNow() {
        codesDirty = true;
        flush(false);
    }

    private YamlConfiguration buildCodes() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("codes");
        ZoneId zone = zone();
        for (Code c : codes.values()) c.save(root.createSection(c.getId()), zone);
        return y;
    }

    private YamlConfiguration buildUsage() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("usage");
        for (Map.Entry<String, Usage> e : usage.entrySet()) {
            Usage u = e.getValue();
            if (u.total == 0 && u.players.isEmpty()) continue;
            ConfigurationSection s = root.createSection(e.getKey());
            s.set("total", u.total);
            ConfigurationSection pl = s.createSection("players");
            for (Map.Entry<UUID, Integer> p : u.players.entrySet()) pl.set(p.getKey().toString(), p.getValue());
        }
        return y;
    }

    private void flush(boolean async) {
        String codesStr = null;
        String usageStr = null;
        if (codesDirty) {
            codesStr = buildCodes().saveToString();
            codesDirty = false;
        }
        if (usageDirty) {
            usageStr = buildUsage().saveToString();
            usageDirty = false;
        }
        if (codesStr == null && usageStr == null) return;
        final String c = codesStr;
        final String u = usageStr;
        Runnable task = () -> {
            synchronized (fileLock) {
                write(codesFile, c);
                write(usageFile, u);
            }
        };
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    private void write(File file, String content) {
        if (content == null) return;
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить " + file.getName() + ": " + e.getMessage());
        }
    }

    private void purge() {
        long now = System.currentTimeMillis();
        lastAttempt.values().removeIf(t -> now - t > 600_000L);
        lockedUntil.values().removeIf(t -> t < now);
    }

    public ZoneId zone() {
        return TimeUtil.zone(plugin.getConfig());
    }

    public String formatDate(long millis) {
        return TimeUtil.format(millis, zone(), plugin.getConfig().getString("settings.date-format", "dd.MM.yyyy HH:mm"));
    }

    public Collection<Code> all() {
        return codes.values();
    }

    public Code byId(String id) {
        return id == null ? null : codes.get(id.toLowerCase(Locale.ROOT));
    }

    public Code find(String input) {
        Code c = byId(input);
        if (c == null) return null;
        if (c.isCaseSensitive() && !c.getId().equals(input)) return null;
        return c;
    }

    public Code create(String id) {
        Code c = new Code(id);
        c.setCaseSensitive(plugin.getConfig().getBoolean("settings.case-sensitive-default", false));
        codes.put(id.toLowerCase(Locale.ROOT), c);
        saveNow();
        return c;
    }

    public void delete(Code c) {
        String low = c.getId().toLowerCase(Locale.ROOT);
        codes.remove(low);
        usage.remove(low);
        usageDirty = true;
        saveNow();
    }

    public int totalUses(Code c) {
        Usage u = usage.get(c.getId().toLowerCase(Locale.ROOT));
        return u == null ? 0 : u.total;
    }

    public void resetUsage(Code c) {
        usage.remove(c.getId().toLowerCase(Locale.ROOT));
        usageDirty = true;
        flush(false);
    }

    public void resetUsage(Code c, UUID player) {
        Usage u = usage.get(c.getId().toLowerCase(Locale.ROOT));
        if (u == null) return;
        Integer n = u.players.remove(player);
        if (n != null) u.total = Math.max(0, u.total - n);
        usageDirty = true;
        flush(false);
    }

    public Generated generate(Code template, int count, int length, String prefix) {
        List<String> ids = new ArrayList<>();
        while (ids.size() < count) {
            StringBuilder sb = new StringBuilder();
            if (!prefix.isEmpty()) sb.append(prefix).append('-');
            for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            String id = sb.toString();
            if (!isValidId(id) || codes.containsKey(id.toLowerCase(Locale.ROOT))) continue;
            Code c = template.copyAs(id);
            c.setMaxUses(1);
            codes.put(id.toLowerCase(Locale.ROOT), c);
            ids.add(id);
        }
        String fileName = template.getId() + "-" + (System.currentTimeMillis() / 1000L) + ".txt";
        try {
            File dir = new File(plugin.getDataFolder(), "generated");

            dir.mkdirs();
            Files.write(new File(dir, fileName).toPath(), ids, StandardCharsets.UTF_8);
            fileName = "generated/" + fileName;
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось записать список кодов: " + e.getMessage());
            fileName = "(ошибка записи)";
        }
        saveNow();
        return new Generated(ids, fileName);
    }

    public void redeem(Player p, String input) {
        FileConfiguration cfg = plugin.getConfig();
        Messages m = plugin.messages();
        long now = System.currentTimeMillis();
        UUID uid = p.getUniqueId();

        if (!p.hasPermission("miragecodes.bypass.lockout")) {
            Long until = lockedUntil.get(uid);
            if (until != null) {
                if (until > now) {
                    deny(p, "locked", "%time%", TimeUtil.duration(until - now));
                    return;
                }
                lockedUntil.remove(uid);
            }
        }
        if (!p.hasPermission("miragecodes.bypass.cooldown")) {
            long cd = cfg.getLong("settings.cooldown-seconds", 3L) * 1000L;
            Long last = lastAttempt.get(uid);
            if (cd > 0 && last != null && now - last < cd) {
                m.send(p, "cooldown", "%time%", TimeUtil.duration(cd - (now - last)));
                return;
            }
        }
        lastAttempt.put(uid, now);

        String typed = input.trim();
        Code code = typed.length() > 64 ? null : find(typed);
        if (code == null) {
            failAttempt(p, now);
            return;
        }

        if (!code.isEnabled()) {
            deny(p, "disabled");
            return;
        }
        if (code.getStart() > now) {
            deny(p, "not-started", "%date%", formatDate(code.getStart()));
            return;
        }
        if (code.getExpires() > 0 && code.getExpires() <= now) {
            deny(p, "expired");
            return;
        }
        if (!code.getPermission().isEmpty() && !p.hasPermission(code.getPermission())) {
            deny(p, "no-code-permission");
            return;
        }

        String low = code.getId().toLowerCase(Locale.ROOT);
        Usage u = usage.computeIfAbsent(low, k -> new Usage());
        if (code.getMaxUses() >= 0 && u.total >= code.getMaxUses()) {
            deny(p, "limit-global");
            return;
        }
        int mine = u.players.getOrDefault(uid, 0);
        if (code.getMaxUsesPerPlayer() >= 0 && mine >= code.getMaxUsesPerPlayer()) {
            deny(p, "limit-player", "%max%", String.valueOf(code.getMaxUsesPerPlayer()));
            return;
        }

        List<Reward> toGive = new ArrayList<>();
        for (Reward r : code.getRewards()) {
            double chance = r.num("chance", 100.0);
            if (chance >= 100.0 || ThreadLocalRandom.current().nextDouble(100.0) < chance) toGive.add(r);
        }

        if ("deny".equalsIgnoreCase(cfg.getString("settings.inventory-full", "drop"))) {
            List<ItemStack> items = new ArrayList<>();
            for (Reward r : toGive) {
                if (!r.type().equals("item")) continue;
                ItemStack it = r.buildItem(s -> s);
                if (it != null) items.add(it);
            }
            if (!items.isEmpty() && !fits(p, items)) {
                deny(p, "inventory-full-deny");
                return;
            }
        }

        u.total++;
        final int playerUses = u.players.merge(uid, 1, Integer::sum);
        usageDirty = true;
        failed.remove(uid);

        final int totalNow = u.total;
        final String maxText = code.getMaxUses() < 0 ? "∞" : String.valueOf(code.getMaxUses());
        UnaryOperator<String> filler = s -> Messages.apply(s,
                "%player%", p.getName(),
                "%uuid%", uid.toString(),
                "%code%", code.getId(),
                "%uses%", String.valueOf(totalNow),
                "%max%", maxText,
                "%player_uses%", String.valueOf(playerUses));

        boolean[] dropNotified = {false};
        for (Reward r : toGive) {
            int delay = r.integer("delay", 0);
            if (delay > 0) {
                boolean consoleCmd = r.type().equals("command") && !r.str("as", "console").equalsIgnoreCase("player");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() || consoleCmd) execute(p, r, filler, dropNotified);
                }, delay);
            } else {
                execute(p, r, filler, dropNotified);
            }
        }

        String custom = code.getSuccessMessage();
        if (!custom.isBlank()) {
            m.sendRaw(p, m.prefix() + filler.apply(custom));
        } else {
            m.send(p, "success", "%code%", code.getId());
        }
        playConfigSound(p, "settings.sounds.success");

        if (cfg.getBoolean("settings.console-log", true)) {
            plugin.getLogger().info(p.getName() + " активировал промокод " + code.getId() + " (" + totalNow + "/" + maxText + ")");
        }
        logToFile(p, code, now);
        sendWebhook(p, code, totalNow, maxText);
    }

    private void failAttempt(Player p, long now) {
        FileConfiguration cfg = plugin.getConfig();
        Messages m = plugin.messages();
        playConfigSound(p, "settings.sounds.fail");
        int max = cfg.getInt("settings.max-failed-attempts", 5);
        if (max <= 0 || p.hasPermission("miragecodes.bypass.lockout")) {
            m.send(p, "invalid", "%left%", "∞");
            return;
        }
        int count = failed.merge(p.getUniqueId(), 1, Integer::sum);
        if (count >= max) {
            long lock = Math.max(1L, cfg.getLong("settings.lockout-seconds", 120L)) * 1000L;
            lockedUntil.put(p.getUniqueId(), now + lock);
            failed.remove(p.getUniqueId());
            m.send(p, "locked", "%time%", TimeUtil.duration(lock));
            plugin.getLogger().warning(p.getName() + " временно заблокирован: слишком много неверных промокодов.");
        } else {
            m.send(p, "invalid", "%left%", String.valueOf(max - count));
        }
    }

    private void deny(Player p, String key, String... pairs) {
        plugin.messages().send(p, key, pairs);
        playConfigSound(p, "settings.sounds.fail");
    }

    private boolean fits(Player p, List<ItemStack> items) {
        Inventory temp = Bukkit.createInventory(null, 36);
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = 0; i < storage.length && i < 36; i++) {
            temp.setItem(i, storage[i] == null ? null : storage[i].clone());
        }
        for (ItemStack it : items) {
            if (!temp.addItem(it.clone()).isEmpty()) return false;
        }
        return true;
    }

    private void execute(Player p, Reward r, UnaryOperator<String> f, boolean[] dropNotified) {
        switch (r.type()) {
            case "command" -> {
                String cmd = f.apply(r.str("command", "")).trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                if (cmd.isEmpty()) return;
                if (r.str("as", "console").equalsIgnoreCase("player")) {
                    p.performCommand(cmd);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
            case "item" -> {
                ItemStack it = r.buildItem(f);
                if (it == null) {
                    plugin.getLogger().warning("Некорректный предмет в награде — пропущен.");
                    return;
                }
                boolean dropped = false;
                for (ItemStack rest : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rest);
                    dropped = true;
                }
                if (dropped && !dropNotified[0]) {
                    dropNotified[0] = true;
                    plugin.messages().send(p, "inventory-full-drop");
                }
            }
            case "message" -> p.sendMessage(Messages.color(f.apply(r.str("text", ""))));
            case "broadcast" -> Bukkit.broadcast(Messages.color(f.apply(r.str("text", ""))));
            case "title" -> {
                Component title = Messages.color(f.apply(r.str("title", "")));
                Component sub = Messages.color(f.apply(r.str("subtitle", "")));
                Title.Times times = Title.Times.times(
                        ticks(r.integer("fade-in", 10)),
                        ticks(r.integer("stay", 60)),
                        ticks(r.integer("fade-out", 10)));
                p.showTitle(Title.title(title, sub, times));
            }
            case "sound" -> playSound(p, r.str("sound", ""), (float) r.num("volume", 1.0), (float) r.num("pitch", 1.0));
            default -> plugin.getLogger().warning("Неизвестный тип награды: " + r.type());
        }
    }

    private static Duration ticks(int t) {
        return Duration.ofMillis(Math.max(0, t) * 50L);
    }

    private void playSound(Player p, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) return;
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (!k.contains(".") && k.contains("_")) k = k.replace('_', '.');
        p.playSound(p.getLocation(), k, volume, pitch);
    }

    private void playConfigSound(Player p, String path) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection(path);
        if (s == null) return;
        playSound(p, s.getString("sound", ""), (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0));
    }

    private void logToFile(Player p, Code code, long now) {
        if (!plugin.getConfig().getBoolean("settings.log-redeems", true)) return;
        String line = "[" + TimeUtil.format(now, zone(), "yyyy-MM-dd HH:mm:ss") + "] "
                + p.getName() + " (" + p.getUniqueId() + ") -> " + code.getId() + System.lineSeparator();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.writeString(logFile.toPath(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().warning("Не удалось записать redeems.log: " + e.getMessage());
            }
        });
    }

    private void sendWebhook(Player p, Code code, int total, String maxText) {
        ConfigurationSection w = plugin.getConfig().getConfigurationSection("settings.discord-webhook");
        if (w == null || !w.getBoolean("enabled", false)) return;
        String url = w.getString("url", "");
        if (url == null || url.isBlank()) return;
        String content = Messages.strip(Messages.apply(
                w.getString("content", "%player% активировал промокод %code%"),
                "%player%", p.getName(),
                "%code%", code.getId(),
                "%uses%", String.valueOf(total),
                "%max%", maxText));
        String json = "{\"content\":\"" + jsonEscape(content) + "\",\"allowed_mentions\":{\"parse\":[]}}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook: " + e.getMessage());
        }
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
