package dev.blockparty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/** Side scoreboard of one arena, shared by everyone in it. */
final class Marcador {

    private static final int MAX_LINES = 15;

    private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    private final Objective objective;
    /** The scoreboard each player had before joining, given back when they leave. */
    private final Map<UUID, Scoreboard> previous = new HashMap<>();
    private int shownLines;

    Marcador(Component title) {
        objective = scoreboard.registerNewObjective("blockparty", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
    }

    void show(Player player) {
        previous.putIfAbsent(player.getUniqueId(), player.getScoreboard());
        player.setScoreboard(scoreboard);
    }

    void hide(Player player) {
        Scoreboard before = previous.remove(player.getUniqueId());
        if (player.getScoreboard() == scoreboard) {
            player.setScoreboard(before != null ? before : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    void title(Component title) {
        objective.displayName(title);
    }

    /** Replaces all lines; the first line goes at the top. */
    void lines(List<Component> lines) {
        int count = Math.min(lines.size(), MAX_LINES);
        for (int i = 0; i < count; i++) {
            // Entries are fixed ids; the visible text is the custom name.
            Score score = objective.getScore("l" + i);
            score.setScore(count - i);
            score.customName(lines.get(i));
        }
        for (int i = count; i < shownLines; i++) {
            scoreboard.resetScores("l" + i);
        }
        shownLines = count;
    }
}
