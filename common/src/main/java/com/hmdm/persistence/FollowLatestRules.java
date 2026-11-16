package com.hmdm.persistence;

import com.hmdm.persistence.domain.Application;

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
        if (stored == null) {
            if (incoming != null) {
                return incoming;
            }
            return savedVersionId == null || followForLinkedVersion(savedVersionId, latestReleasedId);
        }
        if (turnedOn(incoming, stored)) {
            return true;
        }
        final boolean versionChanged = savedVersionId != null && !Objects.equals(savedVersionId, storedVersionId);
        if (versionChanged && !followForLinkedVersion(savedVersionId, latestReleasedId)) {
            return false;
        }
        return incoming != null ? incoming : stored;
    }

    public static boolean movesToLatestOnSave(Boolean incoming, Boolean stored) {
        return Boolean.TRUE.equals(incoming) && !Boolean.TRUE.equals(stored);
    }

    public static boolean followForVersionLink(Application storedRow, int linkAction, int versionId, Integer latestReleasedId) {
        if (storedRow != null && (storedRow.getAction() == 1 || linkAction != 1) && storedRow.isFollowLatest() != null) {
            return storedRow.isFollowLatest();
        }
        return followForLinkedVersion(versionId, latestReleasedId);
    }

    public static String modeForCheckForUpdates(boolean update) {
        return update ? MODE_SCHEDULE : MODE_HOLD;
    }

    private static boolean turnedOn(Boolean incoming, Boolean stored) {
        return Boolean.TRUE.equals(incoming) && Boolean.FALSE.equals(stored);
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
