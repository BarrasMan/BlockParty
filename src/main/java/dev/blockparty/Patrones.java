package dev.blockparty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates the color layout of the floor: an array of indexes into the palette,
 * laid out x-major like the arena floor (index = x * depth + z).
 */
final class Patrones {

    private Patrones() {
    }

    static int[] generate(Ajustes.Patron patron, int width, int depth, int colorCount, Random random) {
        return switch (patron) {
            case CASILLAS -> tiles(width, depth, colorCount, random);
            case FRANJAS -> stripes(width, depth, colorCount, random);
            case ANILLOS -> rings(width, depth, colorCount, random);
            case AJEDREZ -> checker(width, depth, colorCount, random);
            case DIAGONALES -> diagonals(width, depth, colorCount, random);
        };
    }

    static Ajustes.Patron pick(Map<Ajustes.Patron, Double> weights, Random random) {
        double total = 0;
        for (double weight : weights.values()) {
            total += weight;
        }
        double roll = random.nextDouble() * total;
        for (Map.Entry<Ajustes.Patron, Double> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return Ajustes.Patron.CASILLAS;
    }

    /** Mixed squares of 1x1, 2x2 and 3x3, each of a random color. */
    private static int[] tiles(int width, int depth, int colorCount, Random random) {
        int[] grid = new int[width * depth];
        boolean[] filled = new boolean[grid.length];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (filled[x * depth + z]) {
                    continue;
                }
                int size = 1 + random.nextInt(3);
                int color = random.nextInt(colorCount);
                for (int dx = 0; dx < size && x + dx < width; dx++) {
                    for (int dz = 0; dz < size && z + dz < depth; dz++) {
                        int index = (x + dx) * depth + (z + dz);
                        if (!filled[index]) {
                            filled[index] = true;
                            grid[index] = color;
                        }
                    }
                }
            }
        }
        return grid;
    }

    /** Straight stripes of 1-3 blocks, along X or Z. */
    private static int[] stripes(int width, int depth, int colorCount, Random random) {
        int[] grid = new int[width * depth];
        boolean alongX = random.nextBoolean();
        int stripe = 1 + random.nextInt(3);
        int lines = alongX ? depth : width;
        int[] colors = colorSequence((lines + stripe - 1) / stripe, colorCount, random);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                grid[x * depth + z] = colors[(alongX ? z : x) / stripe];
            }
        }
        return grid;
    }

    /** Square rings around the center. */
    private static int[] rings(int width, int depth, int colorCount, Random random) {
        int[] grid = new int[width * depth];
        int ring = 1 + random.nextInt(2);
        double cx = (width - 1) / 2.0;
        double cz = (depth - 1) / 2.0;
        int maxRing = (int) (Math.max(cx, cz) / ring) + 2;
        int[] colors = colorSequence(maxRing, colorCount, random);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int distance = (int) Math.round(Math.max(Math.abs(x - cx), Math.abs(z - cz)));
                grid[x * depth + z] = colors[distance / ring];
            }
        }
        return grid;
    }

    /** Checkerboard of 2-4 colors with squares of 1-3 blocks. */
    private static int[] checker(int width, int depth, int colorCount, Random random) {
        int[] grid = new int[width * depth];
        int square = 1 + random.nextInt(3);
        int[] colors = distinctColors(2 + random.nextInt(Math.min(3, colorCount - 1)), colorCount, random);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                grid[x * depth + z] = colors[(x / square + z / square) % colors.length];
            }
        }
        return grid;
    }

    /** Diagonal bands of 1-3 blocks. */
    private static int[] diagonals(int width, int depth, int colorCount, Random random) {
        int[] grid = new int[width * depth];
        int band = 1 + random.nextInt(3);
        boolean mirrored = random.nextBoolean();
        int[] colors = colorSequence((width + depth) / band + 2, colorCount, random);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int diagonal = mirrored ? x + (depth - 1 - z) : x + z;
                grid[x * depth + z] = colors[diagonal / band];
            }
        }
        return grid;
    }

    /** Random colors where two neighbours are never the same. */
    private static int[] colorSequence(int length, int colorCount, Random random) {
        int[] colors = new int[Math.max(1, length)];
        for (int i = 0; i < colors.length; i++) {
            int color;
            do {
                color = random.nextInt(colorCount);
            } while (i > 0 && color == colors[i - 1] && colorCount > 1);
            colors[i] = color;
        }
        return colors;
    }

    private static int[] distinctColors(int count, int colorCount, Random random) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < colorCount; i++) {
            all.add(i);
        }
        Collections.shuffle(all, random);
        int[] colors = new int[Math.min(count, colorCount)];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = all.get(i);
        }
        return colors;
    }
}
