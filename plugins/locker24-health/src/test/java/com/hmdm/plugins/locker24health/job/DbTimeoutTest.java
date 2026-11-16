package com.hmdm.plugins.locker24health.job;

import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.Test;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DbTimeoutTest {

    @Test
    public void recognisesJdbcTimeouts() {
        assertTrue(DbTimeout.isTimeout(new PersistenceException(new SQLTimeoutException("t"))));
        assertTrue(DbTimeout.isTimeout(new PersistenceException(new RuntimeException(
                new SQLException("canceling statement due to user request", "57014")))));
    }

    @Test
    public void otherFailuresAreNotTimeouts() {
        assertFalse(DbTimeout.isTimeout(new PersistenceException(new SQLException("dup", "23505"))));
        assertFalse(DbTimeout.isTimeout(new IllegalStateException("x")));
        assertFalse(DbTimeout.isTimeout(null));
    }
}
