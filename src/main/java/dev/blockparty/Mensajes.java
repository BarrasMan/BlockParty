package dev.blockparty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * messages_&lt;language&gt;.yml: every text of the plugin, with '&' colors (also "&#RRGGBB") and a prefix.
 * English and Spanish are bundled; any other language works by creating its own file.
 */
final class Mensajes {

    static final String DEFAULT_LANGUAGE = "en";

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private final JavaPlugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();

    Mensajes(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void load(String language) {
        String bundled = "messages_" + language + ".yml";
        if (plugin.getResource(bundled) == null) {
            bundled = "messages_" + DEFAULT_LANGUAGE + ".yml";
        }
        File file = new File(plugin.getDataFolder(), "messages_" + language + ".yml");
        if (!file.exists()) {
            // A language without a bundled file starts as a copy of the English one, ready to translate.
            try (InputStream in = plugin.getResource(bundled)) {
                Files.createDirectories(file.toPath().getParent());
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create " + file.getName(), e);
            }
        }
        messages = YamlConfiguration.loadConfiguration(file);
        InputStream in = plugin.getResource(bundled);
        if (in != null) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Raw text with placeholders applied; falls back to the bundled default. */
    String raw(String key, Map<String, String> placeholders) {
        String text = messages.getString(key, defaults.getString(key, key));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return text;
    }

    Component get(String key, Map<String, String> placeholders) {
        return colorize(raw(key, placeholders));
    }

    Component get(String key) {
        return get(key, Map.of());
    }

    List<Component> list(String key, Map<String, String> placeholders) {
        List<String> lines = messages.contains(key) ? messages.getStringList(key) : defaults.getStringList(key);
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            result.add(colorize(line));
        }
        return result;
    }

    /** Chat message with the configured prefix. */
    void send(CommandSender to, String key, Map<String, String> placeholders) {
        to.sendMessage(colorize(raw("prefijo", Map.of()) + raw(key, placeholders)));
    }

    void send(CommandSender to, String key) {
        send(to, key, Map.of());
    }

    static Component colorize(String text) {
        return LEGACY.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
