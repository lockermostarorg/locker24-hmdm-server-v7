package com.hmdm.persistence.domain;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationNewCopyTest {

    @Test
    public void copyKeepsPinnedMainAndContentApps() {
        Configuration source = new Configuration();
        source.setFollowLatestMainApp(false);
        source.setFollowLatestContentApp(false);

        Configuration copy = source.newCopy();

        assertFalse(copy.isFollowLatestMainApp());
        assertFalse(copy.isFollowLatestContentApp());
    }

    @Test
    public void copyKeepsFollowingMainAndContentApps() {
        Configuration copy = new Configuration().newCopy();

        assertTrue(copy.isFollowLatestMainApp());
        assertTrue(copy.isFollowLatestContentApp());
    }
}
