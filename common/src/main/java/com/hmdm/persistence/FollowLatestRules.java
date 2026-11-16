package com.hmdm.persistence;

import java.util.Objects;

public final class FollowLatestRules {

    public static final String MODE_SCHEDULE = "schedule";
    public static final String MODE_IMMEDIATE = "immediate";
    public static final String MODE_HOLD = "hold";

    public static final String STATE_RELEASED = "released";
    public static final String STATE_HELD = "held";

    private FollowLatestRules() {
    }

    public static boolean followForLinkedVersion(int chosenVersionId, Integer latestReleasedId) {
        return latestReleasedId != null && latestReleasedId == chosenVersionId;
    }

    public static boolean followForSavedRow(Boolean incoming,
                                            Boolean stored,
                                            Integer storedVersionId,
                                            Integer savedVersionId,
                                            Integer latestReleasedId) {
        if (incoming != null) {
            return incoming;
        }
        if (savedVersionId == null) {
            return stored == null || stored;
        }
        if (stored == null) {
            return followForLinkedVersion(savedVersionId, latestReleasedId);
        }
        if (Objects.equals(savedVersionId, storedVersionId)) {
            return stored;
        }
        return stored && followForLinkedVersion(savedVersionId, latestReleasedId);
    }

    public static boolean followForConfigurationFlag(Boolean incoming, Boolean stored) {
        if (incoming != null) {
            return incoming;
        }
        return stored == null || stored;
    }

    public static String normalizeUploadMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return MODE_SCHEDULE;
        }
        if (MODE_SCHEDULE.equals(mode) || MODE_IMMEDIATE.equals(mode) || MODE_HOLD.equals(mode)) {
            return mode;
        }
        throw new IllegalArgumentException("Unknown release mode: " + mode);
    }

    public static String normalizeReleaseMode(String mode) {
        String normalized = normalizeUploadMode(mode);
        if (MODE_HOLD.equals(normalized)) {
            throw new IllegalArgumentException("A version cannot be released with mode " + MODE_HOLD);
        }
        return normalized;
    }
}
