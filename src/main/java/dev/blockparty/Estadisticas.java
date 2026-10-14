package dev.blockparty;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Games played, wins and best round of every player (estadisticas.yml). */
final class Estadisticas {

    static final class Fila {
        String name;
        int played;
        int wins;
        int bestRound;
    }

    private final File file;
    private final Logger logger;
    private final Map<UUID, Fila> rows = new HashMap<>();

    Estadisticas(File folder, Logger logger) {
        this.file = new File(folder, "estadisticas.yml");
        this.logger = logger;
    }

    void load() {
        rows.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("jugadores");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                Fila row = new Fila();
                row.name = players.getString(key + ".nombre", "?");
                row.played = players.getInt(key + ".jugadas");
                row.wins = players.getInt(key + ".victorias");
                row.bestRound = players.getInt(key + ".mejor-ronda");
                rows.put(UUID.fromString(key), row);
            } catch (IllegalArgumentException ignored) {
                // Not a UUID: skip.
            }
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("BlockParty statistics per player."));
        rows.forEach((id, row) -> {
            String path = "jugadores." + id;
            yaml.set(path + ".nombre", row.name);
            yaml.set(path + ".jugadas", row.played);
            yaml.set(path + ".victorias", row.wins);
            yaml.set(path + ".mejor-ronda", row.bestRound);
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save estadisticas.yml", e);
        }
    }

    /** Records one finished game for this player (call save() afterwards). */
    void record(UUID id, String name, boolean won, int round) {
        Fila row = rows.computeIfAbsent(id, k -> new Fila());
        row.name = name;
        row.played++;
        if (won) {
            row.wins++;
        }
        row.bestRound = Math.max(row.bestRound, round);
    }

    Fila get(UUID id) {
        return rows.get(id);
    }

    Fila findByName(String name) {
        for (Fila row : rows.values()) {
            if (row.name != null && row.name.equalsIgnoreCase(name)) {
                return row;
            }
        }
        return null;
    }
}
