package org.example.sawervatoryy.mirageCodes;

import org.bukkit.configuration.ConfigurationSection;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Code {

    private final String id;
    private boolean enabled = true;
    private boolean caseSensitive = false;
    private int maxUses = -1;
    private int maxUsesPerPlayer = 1;
    private long start = 0L;
    private long expires = 0L;
    private String permission = "";
    private String successMessage = "";
    private final List<Reward> rewards = new ArrayList<>();

    public Code(String id) {
        this.id = id;
    }

    public static Code load(String id, ConfigurationSection s, ZoneId zone, boolean defaultCase, List<String> warnings) {
        Code c = new Code(id);
        c.enabled = s.getBoolean("enabled", true);
        c.caseSensitive = s.getBoolean("case-sensitive", defaultCase);
        c.maxUses = s.getInt("max-uses", -1);
        c.maxUsesPerPlayer = s.getInt("max-uses-per-player", 1);
        c.permission = s.getString("permission", "");
        c.successMessage = s.getString("message", "");
        try {
            c.start = readTime(s.get("start"), zone);
            c.expires = readTime(s.get("expires"), zone);
        } catch (IllegalArgumentException e) {
            c.enabled = false;
            warnings.add("Промокод " + id + ": неверная дата в start/expires — код отключён. " + e.getMessage());
        }
        int i = 1;
        for (Map<?, ?> raw : s.getMapList("rewards")) {
            Reward r = new Reward(raw);
            String err = r.validate();
            if (err != null) warnings.add("Промокод " + id + ": награда #" + i + " — " + err);
            c.rewards.add(r);
            i++;
        }
        return c;
    }

    private static long readTime(Object o, ZoneId zone) {
        if (o == null) return 0L;
        if (o instanceof Date d) return d.getTime();
        if (o instanceof Number n) return n.longValue();
        return TimeUtil.parse(String.valueOf(o), zone, false);
    }

    public void save(ConfigurationSection s, ZoneId zone) {
        s.set("enabled", enabled);
        s.set("case-sensitive", caseSensitive);
        s.set("max-uses", maxUses);
        s.set("max-uses-per-player", maxUsesPerPlayer);
        s.set("start", start > 0 ? TimeUtil.format(start, zone, TimeUtil.STORE_PATTERN) : "none");
        s.set("expires", expires > 0 ? TimeUtil.format(expires, zone, TimeUtil.STORE_PATTERN) : "never");
        s.set("permission", permission);
        s.set("message", successMessage);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Reward r : rewards) list.add(new LinkedHashMap<>(r.asMap()));
        s.set("rewards", list);
    }

    public Code copyAs(String newId) {
        Code c = new Code(newId);
        c.enabled = enabled;
        c.caseSensitive = caseSensitive;
        c.maxUses = maxUses;
        c.maxUsesPerPlayer = maxUsesPerPlayer;
        c.start = start;
        c.expires = expires;
        c.permission = permission;
        c.successMessage = successMessage;
        for (Reward r : rewards) c.rewards.add(r.copy());
        return c;
    }

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }
    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
    public int getMaxUsesPerPlayer() { return maxUsesPerPlayer; }
    public void setMaxUsesPerPlayer(int v) { this.maxUsesPerPlayer = v; }
    public long getStart() { return start; }
    public void setStart(long start) { this.start = start; }
    public long getExpires() { return expires; }
    public void setExpires(long expires) { this.expires = expires; }
    public String getPermission() { return permission == null ? "" : permission; }
    public void setPermission(String permission) { this.permission = permission == null ? "" : permission; }
    public String getSuccessMessage() { return successMessage == null ? "" : successMessage; }
    public void setSuccessMessage(String m) { this.successMessage = m == null ? "" : m; }
    public List<Reward> getRewards() { return rewards; }
}
