package com.hmdm.persistence.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FollowLatestJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void rowFlagIsWrittenToResponseJson() throws Exception {
        Application row = new Application();
        row.setFollowLatest(false);

        String json = mapper.writeValueAsString(row);

        assertTrue(json, json.contains("\"followLatest\":false"));
    }

    @Test
    public void omittedRowFlagReadsAsNull() throws Exception {
        Application row = mapper.readValue("{\"id\":5}", Application.class);

        assertNull(row.isFollowLatest());
    }

    @Test
    public void omittedConfigurationFlagsReadAsNull() throws Exception {
        Configuration c = mapper.readValue("{\"id\":5}", Configuration.class);

        assertNull(c.getFollowLatestMainApp());
        assertNull(c.getFollowLatestContentApp());
    }

    @Test
    public void configurationFlagsRoundTrip() throws Exception {
        Configuration c = mapper.readValue("{\"followLatestMainApp\":false,\"followLatestContentApp\":true}",
                Configuration.class);

        assertEquals(Boolean.FALSE, c.getFollowLatestMainApp());
        String json = mapper.writeValueAsString(c);
        assertTrue(json, json.contains("\"followLatestMainApp\":false"));
        assertTrue(json, json.contains("\"followLatestContentApp\":true"));
    }
}
