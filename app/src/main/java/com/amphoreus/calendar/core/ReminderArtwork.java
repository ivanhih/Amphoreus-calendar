package com.amphoreus.calendar.core;

import java.util.Random;

/** Chooses a fresh image whenever a reminder in random-art mode is built. */
public final class ReminderArtwork {
    public static final int COUNT=13;
    public static int randomIndex(Random random) { return random.nextInt(COUNT); }
    private ReminderArtwork() {}
}
