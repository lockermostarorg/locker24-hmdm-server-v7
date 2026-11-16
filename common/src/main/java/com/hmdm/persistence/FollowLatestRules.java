package com.hmdm.persistence;

import com.hmdm.persistence.domain.Application;


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

    public static boolean followForSaved(Boolean incoming, Boolean stored, Integer savedVersionId, Integer latestReleasedId) {
        if (incoming != null) {
            return incoming;
        }
        if (stored != null) {
            return stored;
        }
        return savedVersionId == null || followForLinkedVersion(savedVersionId, latestReleasedId);
    }

    public static Integer versionForSaved(boolean follow, Integer savedVersionId, Integer latestReleasedId) {
        return follow && latestReleasedId != null ? latestReleasedId : savedVersionId;
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
