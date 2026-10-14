package dev.blockparty;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/** One floor color: the block used and how it is announced (Spanish name and text color). */
record ColorBP(String id, Material material, BlockData data, String name, TextColor textColor) {

    /** "&#RRGGBB" so the name can be inserted into any '&' formatted message. */
    String legacyColor() {
        return "&" + textColor.asHexString();
    }
}
