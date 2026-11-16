package com.hmdm.persistence.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ApplicationVersionJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void onlyReleaseModeIsReadFromRequestJson() throws Exception {
        ApplicationVersion version = mapper.readValue(
                "{\"version\":\"1.0\",\"releaseState\":\"held\",\"releasedAt\":5,\"releaseMode\":\"immediate\",\"releasedBy\":7,\"releasedByName\":\"Mallory\"}",
                ApplicationVersion.class);

        assertEquals("1.0", version.getVersion());
        assertNull(version.getReleaseState());
        assertNull(version.getReleasedAt());
        assertEquals("immediate", version.getReleaseMode());
        assertNull(version.getReleasedBy());
        assertNull(version.getReleasedByName());
    }

    @Test
    public void releaseFieldsAreWrittenToResponseJson() throws Exception {
        ApplicationVersion version = new ApplicationVersion();
        version.setReleaseState("held");
        version.setReleasedAt(5L);
        version.setReleaseMode("schedule");
        version.setReleasedBy(7);
        version.setReleasedByName("Ana");

        String json = mapper.writeValueAsString(version);

        assertTrue(json, json.contains("\"releaseState\":\"held\""));
        assertTrue(json, json.contains("\"releasedAt\":5"));
        assertTrue(json, json.contains("\"releaseMode\":\"schedule\""));
        assertTrue(json, json.contains("\"releasedBy\":7"));
        assertTrue(json, json.contains("\"releasedByName\":\"Ana\""));
    }

    @Test
    public void serverCodeCanStillSetReleaseFields() {
        ApplicationVersion version = new ApplicationVersion();
        version.setReleaseState("held");

        assertEquals("held", version.getReleaseState());
    }
}
