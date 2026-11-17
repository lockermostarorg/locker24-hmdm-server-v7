package com.hmdm.persistence;

import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.persistence.mapper.ApplicationMapper;
import com.hmdm.util.APKFileAnalyzer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApplicationOrphanedFilesTest {

    private static final String BASE_URL = "http://localhost";

    private File filesDirectory;
    private ApplicationMapper mapper;
    private ApplicationDAO dao;
    private Customer customer;

    @Before
    public void setUp() throws Exception {
        filesDirectory = Files.createTempDirectory("orphaned").toFile();
        mapper = mock(ApplicationMapper.class);
        dao = new ApplicationDAO(mapper, mock(CustomerDAO.class), filesDirectory.getAbsolutePath(), BASE_URL, "",
                mock(APKFileAnalyzer.class));
        customer = new Customer();
        customer.setId(1);
        customer.setFilesDir("");
        when(mapper.getCustomerFreeTextUrls(1)).thenReturn(Collections.emptyList());
        when(mapper.getCustomerConfigurationFilePaths(1)).thenReturn(Collections.emptyList());
    }

    @After
    public void tearDown() {
        File[] files = filesDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        filesDirectory.delete();
    }

    @Test
    public void aFileReferencedByAnyVersionOfAnyCustomerIsNotOrphaned() throws Exception {
        for (String name : new String[] {"own.apk", "other-customer.apk", "held-1.2.apk", "orphan.apk"}) {
            new File(filesDirectory, name).createNewFile();
        }
        when(mapper.getAllApplicationVersionUrls()).thenReturn(Arrays.asList(
                version(BASE_URL + "/files/own.apk"),
                version(BASE_URL + "/files/other-customer.apk"),
                version(BASE_URL + "/files/held-1.2.apk")));
        when(mapper.getCustomerApplicationVersionUrls(1)).thenReturn(Collections.singletonList(
                version(BASE_URL + "/files/own.apk")));

        assertEquals(Collections.singletonList("orphan.apk"), dao.getOrphanedFiles(customer));
    }

    private static ApplicationVersion version(String url) {
        ApplicationVersion version = new ApplicationVersion();
        version.setUrl(url);
        return version;
    }
}
