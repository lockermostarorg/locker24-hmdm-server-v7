package com.hmdm.rest.resource;

import com.hmdm.rest.json.Response;

import java.sql.SQLException;

final class ReleaseErrors {

    static final String DEADLOCK = "40P01";
    static final String SERIALIZATION_FAILURE = "40001";

    private ReleaseErrors() {
    }

    static boolean isConcurrencyFailure(Throwable error) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 20; t = t.getCause(), depth++) {
            if (t instanceof SQLException) {
                final String state = ((SQLException) t).getSQLState();
                if (DEADLOCK.equals(state) || SERIALIZATION_FAILURE.equals(state)) {
                    return true;
                }
            }
        }
        return false;
    }

    static Response unexpected(Exception error) {
        if (isConcurrencyFailure(error)) {
            return Response.ERROR("error.concurrent.update");
        }
        return Response.INTERNAL_ERROR();
    }
}
