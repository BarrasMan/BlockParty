package dev.blockparty;

import java.util.ArrayList;
import java.util.List;

/**
 * A melody played with note block sounds. Written as notes separated by spaces:
 * a number 0-24 is a note block note (0 = F#3, 12 = F#4, 24 = F#5), '-' is a rest
 * and "a+b" plays several notes at once.
 */
final class Melodia {

    private final String id;
    private final String sound;
    private final int ticksPerNote;
    /** One entry per step; each step holds the pitches to play (empty = rest). */
    private final List<float[]> steps;

    private Melodia(String id, String sound, int ticksPerNote, List<float[]> steps) {
        this.id = id;
        this.sound = sound;
        this.ticksPerNote = ticksPerNote;
        this.steps = steps;
    }

    static Melodia parse(String id, String sound, int ticksPerNote, String notes) {
        List<float[]> steps = new ArrayList<>();
        boolean anyNote = false;
        for (String token : notes.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.equals("-")) {
                steps.add(new float[0]);
                continue;
            }
            List<Float> pitches = new ArrayList<>();
            for (String part : token.split("\\+")) {
                try {
                    int note = Integer.parseInt(part);
                    if (note >= 0 && note <= 24) {
                        pitches.add(pitchOf(note));
                    }
                } catch (NumberFormatException ignored) {
                    // Invalid notes are skipped.
                }
            }
            float[] step = new float[pitches.size()];
            for (int i = 0; i < step.length; i++) {
                step[i] = pitches.get(i);
            }
            anyNote |= step.length > 0;
            steps.add(step);
        }
        return new Melodia(id, sound, Math.max(1, ticksPerNote), anyNote ? steps : List.of());
    }

    /** Note block pitch: note 12 is the base pitch 1.0, one octave doubles it. */
    static float pitchOf(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    boolean isEmpty() {
        return steps.isEmpty();
    }

    String id() {
        return id;
    }

    String sound() {
        return sound;
    }

    int ticksPerNote() {
        return ticksPerNote;
    }

    int length() {
        return steps.size();
    }

    float[] step(int index) {
        return steps.get(index % steps.size());
    }
}
