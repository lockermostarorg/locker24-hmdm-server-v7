package com.hmdm.guice.module;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigureModuleTest {

    @Test
    public void missingValueFallsBack() {
        assertEquals("Locker24 MDM", ConfigureModule.rebrandingValue(null, "Locker24 MDM"));
    }

    @Test
    public void emptyValueFallsBack() {
        assertEquals("Locker24 MDM", ConfigureModule.rebrandingValue("", "Locker24 MDM"));
    }

    @Test
    public void unresolvedPlaceholderFallsBack() {
        assertEquals("Locker24 MDM", ConfigureModule.rebrandingValue("${rebranding.name}", "Locker24 MDM"));
    }

    @Test
    public void realValueIsKept() {
        assertEquals("Acme MDM", ConfigureModule.rebrandingValue("Acme MDM", "Locker24 MDM"));
    }

    @Test
    public void blankDefaultIsAllowedForOptionalLinks() {
        assertEquals("", ConfigureModule.rebrandingValue("${rebranding.terms.link}", ""));
    }
}
