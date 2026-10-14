package dev.blockparty;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Everything that touches WorldEdit. Only used when WorldEdit is installed. */
final class SeleccionWorldEdit {

    private SeleccionWorldEdit() {
    }

    /** The two corners of the player's WorldEdit selection, or null if there's none. */
    static Location[] corners(Player player) {
        try {
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
            com.sk89q.worldedit.world.World selectionWorld = session.getSelectionWorld();
            if (selectionWorld == null) {
                return null;
            }
            Region region = session.getSelection(selectionWorld);
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            World world = BukkitAdapter.adapt(selectionWorld);
            return new Location[] {
                new Location(world, min.x(), min.y(), min.z()),
                new Location(world, max.x(), max.y(), max.z())
            };
        } catch (Exception e) {
            // IncompleteRegionException and anything else: there's no usable selection.
            return null;
        }
    }
}
