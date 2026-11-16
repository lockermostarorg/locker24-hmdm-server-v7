package com.hmdm.plugins.locker24health.job;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;

public final class DbTimeout {

    static final String QUERY_CANCELED = "57014";

    private DbTimeout() {
    }

    public static boolean isTimeout(Throwable t) {
        for (int depth = 0; t != null && depth < 16; depth++, t = t.getCause()) {
            if (t instanceof SQLTimeoutException) {
                return true;
            }
            if (t instanceof SQLException && QUERY_CANCELED.equals(((SQLException) t).getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
