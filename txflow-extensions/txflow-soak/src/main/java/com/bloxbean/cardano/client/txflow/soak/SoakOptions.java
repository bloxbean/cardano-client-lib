package com.bloxbean.cardano.client.txflow.soak;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code --key=value} command-line options, in the same style as the verified-structures
 * load tools.
 */
public final class SoakOptions {

    private final Map<String, String> values = new LinkedHashMap<>();

    private SoakOptions() {
    }

    public static SoakOptions parse(String[] args) {
        SoakOptions options = new SoakOptions();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument '" + arg
                        + "' (options look like --key=value)");
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq < 0) {
                options.values.put(body.toLowerCase(), "true");   // bare flag
            } else {
                options.values.put(body.substring(0, eq).toLowerCase(), body.substring(eq + 1));
            }
        }
        return options;
    }

    public boolean has(String key) {
        return values.containsKey(key.toLowerCase());
    }

    public String string(String key, String defaultValue) {
        return values.getOrDefault(key.toLowerCase(), defaultValue);
    }

    public int integer(String key, int defaultValue) {
        String raw = values.get(key.toLowerCase());
        return raw == null ? defaultValue : Integer.parseInt(raw.trim());
    }

    public double decimal(String key, double defaultValue) {
        String raw = values.get(key.toLowerCase());
        return raw == null ? defaultValue : Double.parseDouble(raw.trim());
    }

    public boolean flag(String key, boolean defaultValue) {
        String raw = values.get(key.toLowerCase());
        return raw == null ? defaultValue : Boolean.parseBoolean(raw.trim());
    }

    public Path path(String key, String defaultValue) {
        return Path.of(string(key, defaultValue));
    }

    /**
     * Parse a duration written the way an operator would write it: {@code 90s}, {@code 30m},
     * {@code 12h}, {@code 2d}. A bare number is read as minutes.
     */
    public Duration duration(String key, String defaultValue) {
        String raw = string(key, defaultValue).trim().toLowerCase();
        if (raw.isEmpty()) throw new IllegalArgumentException("empty duration for --" + key);

        char unit = raw.charAt(raw.length() - 1);
        if (Character.isDigit(unit)) {
            return Duration.ofMinutes(Long.parseLong(raw));
        }
        long amount = Long.parseLong(raw.substring(0, raw.length() - 1).trim());
        switch (unit) {
            case 's': return Duration.ofSeconds(amount);
            case 'm': return Duration.ofMinutes(amount);
            case 'h': return Duration.ofHours(amount);
            case 'd': return Duration.ofDays(amount);
            default:
                throw new IllegalArgumentException("Unknown duration unit '" + unit
                        + "' in --" + key + "=" + raw + " (use s, m, h or d)");
        }
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
