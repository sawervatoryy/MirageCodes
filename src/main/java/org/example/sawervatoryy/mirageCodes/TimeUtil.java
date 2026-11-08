package org.example.sawervatoryy.mirageCodes;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    public static final String STORE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final Pattern RELATIVE = Pattern.compile("^(\\d{1,6})([smhdw])$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter[] DATE_TIME = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    };
    private static final DateTimeFormatter[] DATE_ONLY = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
    };

    private TimeUtil() {
    }

    public static ZoneId zone(FileConfiguration cfg) {
        String tz = cfg.getString("settings.timezone", "system");
        try {
            if (tz == null || tz.equalsIgnoreCase("system")) return ZoneId.systemDefault();
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public static long parse(String input, ZoneId zone, boolean allowRelative) {
        if (input == null) return 0L;
        String s = input.trim();
        if (s.isEmpty()) return 0L;
        String low = s.toLowerCase(Locale.ROOT);
        if (low.equals("never") || low.equals("none") || low.equals("off") || low.equals("-")
                || low.equals("никогда") || low.equals("нет")) {
            return 0L;
        }
        if (allowRelative) {
            Matcher m = RELATIVE.matcher(s);
            if (m.matches()) {
                long n = Long.parseLong(m.group(1));
                long unit = switch (Character.toLowerCase(m.group(2).charAt(0))) {
                    case 's' -> 1000L;
                    case 'm' -> 60_000L;
                    case 'h' -> 3_600_000L;
                    case 'd' -> 86_400_000L;
                    default -> 604_800_000L;
                };
                return System.currentTimeMillis() + n * unit;
            }
        }
        for (DateTimeFormatter f : DATE_TIME) {
            try {
                return LocalDateTime.parse(s, f).atZone(zone).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter f : DATE_ONLY) {
            try {
                return LocalDate.parse(s, f).atStartOfDay(zone).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("Неверная дата: " + s);
    }

    public static String format(long millis, ZoneId zone, String pattern) {
        DateTimeFormatter f;
        try {
            f = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        }
        return f.format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    public static String duration(long ms) {
        long total = Math.max(1L, (ms + 999L) / 1000L);
        long d = total / 86_400;
        long h = (total % 86_400) / 3_600;
        long m = (total % 3_600) / 60;
        long s = total % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("д ");
        if (h > 0) sb.append(h).append("ч ");
        if (m > 0) sb.append(m).append("м ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("с");
        return sb.toString().trim();
    }
}
