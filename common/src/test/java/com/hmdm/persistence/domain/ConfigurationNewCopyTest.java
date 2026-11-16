package com.hmdm.persistence.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigurationNewCopyTest {

    @Test
    public void copyKeepsPinnedMainAndContentApps() {
        Configuration source = new Configuration();
        source.setFollowLatestMainApp(false);
        source.setFollowLatestContentApp(false);

        Configuration copy = source.newCopy();

        assertEquals(Boolean.FALSE, copy.getFollowLatestMainApp());
        assertEquals(Boolean.FALSE, copy.getFollowLatestContentApp());
    }

    @Test
    public void copyKeepsFollowingMainAndContentApps() {
        Configuration source = new Configuration();
        source.setFollowLatestMainApp(true);
        source.setFollowLatestContentApp(true);

        Configuration copy = source.newCopy();

        assertEquals(Boolean.TRUE, copy.getFollowLatestMainApp());
        assertEquals(Boolean.TRUE, copy.getFollowLatestContentApp());
    }
}
