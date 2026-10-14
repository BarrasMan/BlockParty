package dev.blockparty;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Every value of config.yml, read once and validated. */
final class Ajustes {

    enum Patron { CASILLAS, FRANJAS, ANILLOS, AJEDREZ, DIAGONALES }

    enum Potenciador { VELOCIDAD, SUPERSALTO, ADELANTO, CEGUERA, BLOQUE_EXTRA }

    /** Language of the messages file (messages_&lt;language&gt;.yml). */
    String language;

    // Players and lobby
    int minPlayers;
    int maxPlayers;
    int countdown;
    int countdownFull;

    // Rounds
    int maxRounds;
    double musicStart;
    double musicMin;
    double reactionStart;
    double reactionMin;
    int roundsToMin;
    double fallPause;
    int fallDepth;
    int endSeconds;

    // Floor
    int maxFloorSize;
    int blocksPerTick;
    final List<ColorBP> colors = new ArrayList<>();
    final Map<Patron, Double> patterns = new EnumMap<>(Patron.class);

    // Power-ups
    boolean powerupsEnabled;
    double powerupChance;
    Material powerupMaterial;
    final Map<Potenciador, Double> powerups = new EnumMap<>(Potenciador.class);
    int speedSeconds;
    int jumpSeconds;
    int blindSeconds;

    // Leave item
    boolean leaveItemEnabled;
    Material leaveItemMaterial;
    int leaveItemSlot;

    // Protection
    final List<String> allowedCommands = new ArrayList<>();

    // Rewards
    double winnerMoney;
    boolean splitWinnerMoney;
    double participationMoney;
    int minPlayersForRewards;
    final List<String> winnerCommands = new ArrayList<>();
    final List<String> participationCommands = new ArrayList<>();
    boolean announceGlobally;

    // Sounds and music
    float musicVolume;
    final Map<String, String> sounds = new LinkedHashMap<>();
    final List<Melodia> melodies = new ArrayList<>();

    void load(FileConfiguration c, Logger log) {
        language = c.getString("language", Mensajes.DEFAULT_LANGUAGE).trim().toLowerCase(Locale.ROOT);
        if (!language.matches("[a-z0-9_-]{1,16}")) {
            log.warning("Invalid language '" + language + "'; using " + Mensajes.DEFAULT_LANGUAGE + ".");
            language = Mensajes.DEFAULT_LANGUAGE;
        }
        minPlayers = Math.max(1, c.getInt("jugadores.minimo", 2));
        maxPlayers = Math.max(minPlayers, c.getInt("jugadores.maximo", 16));
        countdown = Math.max(1, c.getInt("sala-espera.cuenta-atras", 30));
        countdownFull = Math.max(1, c.getInt("sala-espera.cuenta-atras-llena", 10));

        maxRounds = Math.max(1, c.getInt("rondas.maximo", 25));
        musicStart = c.getDouble("rondas.musica-inicial", 10);
        musicMin = Math.min(musicStart, c.getDouble("rondas.musica-minima", 3));
        reactionStart = c.getDouble("rondas.reaccion-inicial", 4);
        reactionMin = Math.min(reactionStart, c.getDouble("rondas.reaccion-minima", 1.5));
        roundsToMin = Math.max(2, c.getInt("rondas.rondas-hasta-minimo", 20));
        fallPause = Math.max(0.5, c.getDouble("rondas.pausa-tras-quitar", 2));
        fallDepth = Math.max(1, c.getInt("rondas.bloques-de-caida", 2));
        endSeconds = Math.max(1, c.getInt("rondas.segundos-final", 6));

        maxFloorSize = Math.max(4, c.getInt("suelo.tamano-maximo", 64));
        blocksPerTick = Math.max(64, c.getInt("suelo.bloques-por-tick", 1024));

        loadColors(c, log);

        patterns.clear();
        ConfigurationSection patternSection = c.getConfigurationSection("suelo.patrones");
        for (Patron patron : Patron.values()) {
            double weight = patternSection == null ? 1 : patternSection.getDouble(patron.name().toLowerCase(Locale.ROOT), 0);
            if (weight > 0) {
                patterns.put(patron, weight);
            }
        }
        if (patterns.isEmpty()) {
            patterns.put(Patron.CASILLAS, 1.0);
        }

        powerupsEnabled = c.getBoolean("potenciadores.activado", true);
        powerupChance = c.getDouble("potenciadores.probabilidad", 40);
        Material material = Material.matchMaterial(c.getString("potenciadores.objeto", "NETHER_STAR"));
        powerupMaterial = material == null || !material.isItem() ? Material.NETHER_STAR : material;
        powerups.clear();
        ConfigurationSection types = c.getConfigurationSection("potenciadores.tipos");
        for (Potenciador type : Potenciador.values()) {
            String key = type.name().toLowerCase(Locale.ROOT).replace('_', '-');
            double weight = types == null ? 1 : types.getDouble(key, 0);
            if (weight > 0) {
                powerups.put(type, weight);
            }
        }
        speedSeconds = c.getInt("potenciadores.segundos-velocidad", 6);
        jumpSeconds = c.getInt("potenciadores.segundos-supersalto", 6);
        blindSeconds = c.getInt("potenciadores.segundos-ceguera", 3);

        leaveItemEnabled = c.getBoolean("objeto-salir.activado", true);
        Material leaveMaterial = Material.matchMaterial(c.getString("objeto-salir.objeto", "BARRIER"));
        leaveItemMaterial = leaveMaterial == null || !leaveMaterial.isItem() || leaveMaterial.isAir()
                ? Material.BARRIER : leaveMaterial;
        leaveItemSlot = Math.max(0, Math.min(8, c.getInt("objeto-salir.hueco", 8)));

        allowedCommands.clear();
        for (String command : c.getStringList("comandos-permitidos")) {
            allowedCommands.add(command.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }

        winnerMoney = c.getDouble("recompensas.dinero-ganador", 50);
        splitWinnerMoney = c.getBoolean("recompensas.repartir-entre-ganadores", true);
        participationMoney = c.getDouble("recompensas.dinero-participar", 5);
        minPlayersForRewards = c.getInt("recompensas.jugadores-minimos", 2);
        winnerCommands.clear();
        winnerCommands.addAll(c.getStringList("recompensas.comandos-ganador"));
        participationCommands.clear();
        participationCommands.addAll(c.getStringList("recompensas.comandos-participar"));
        announceGlobally = c.getBoolean("recompensas.anunciar-a-todo-el-servidor", true);

        musicVolume = (float) c.getDouble("musica.volumen", 1.0);
        sounds.clear();
        ConfigurationSection soundSection = c.getConfigurationSection("sonidos");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, soundSection.getString(key));
            }
        }
        melodies.clear();
        ConfigurationSection melodySection = c.getConfigurationSection("musica.melodias");
        if (melodySection != null) {
            for (String id : melodySection.getKeys(false)) {
                ConfigurationSection m = melodySection.getConfigurationSection(id);
                if (m == null) {
                    continue;
                }
                Melodia melodia = Melodia.parse(id, m.getString("sonido", "block.note_block.harp"),
                        m.getInt("ticks-por-nota", 3), m.getString("notas", ""));
                if (melodia.isEmpty()) {
                    log.warning("Melody '" + id + "' has no valid notes; ignored.");
                } else {
                    melodies.add(melodia);
                }
            }
        }
        if (melodies.isEmpty()) {
            melodies.add(Melodia.parse("basica", "block.note_block.harp", 3, "6 10 13 18 13 10"));
        }
    }

    private void loadColors(FileConfiguration c, Logger log) {
        colors.clear();
        String type = c.getString("suelo.tipo-bloque", "WOOL").toUpperCase(Locale.ROOT);
        ConfigurationSection list = c.getConfigurationSection("suelo.colores");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                ConfigurationSection entry = list.getConfigurationSection(id);
                String name = entry == null ? id : entry.getString("nombre", id);
                String hex = entry == null ? "#FFFFFF" : entry.getString("color", "#FFFFFF");
                String materialName = entry != null && entry.contains("material")
                        ? entry.getString("material")
                        : id.toUpperCase(Locale.ROOT) + "_" + type;
                Material material = Material.matchMaterial(materialName);
                TextColor textColor = TextColor.fromHexString(hex.startsWith("#") ? hex : "#" + hex);
                if (material == null || !material.isBlock()) {
                    log.warning("Color '" + id + "': block '" + materialName + "' does not exist; ignored.");
                    continue;
                }
                colors.add(new ColorBP(id, material, material.createBlockData(), name,
                        textColor == null ? TextColor.color(0xFFFFFF) : textColor));
            }
        }
        if (colors.size() < 2) {
            log.warning("suelo.colores needs at least 2 colors; using red and blue.");
            colors.clear();
            colors.add(new ColorBP("RED", Material.RED_WOOL, Material.RED_WOOL.createBlockData(), "ROJO",
                    TextColor.color(0xE53935)));
            colors.add(new ColorBP("BLUE", Material.BLUE_WOOL, Material.BLUE_WOOL.createBlockData(), "AZUL",
                    TextColor.color(0x1E40AF)));
        }
    }

    /** Seconds of music for this round (decreases linearly to the minimum). */
    double musicSeconds(int round) {
        return interpolate(musicStart, musicMin, round);
    }

    /** Seconds to reach the right color for this round. */
    double reactionSeconds(int round) {
        return interpolate(reactionStart, reactionMin, round);
    }

    private double interpolate(double start, double min, int round) {
        double progress = Math.min(1, Math.max(0, (round - 1) / (double) (roundsToMin - 1)));
        return start - (start - min) * progress;
    }

    String sound(String key) {
        return sounds.get(key);
    }
}
