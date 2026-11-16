package com.hmdm.rest.resource;

import com.hmdm.rest.json.Response;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;

import java.sql.SQLException;

final class ReleaseErrors {

    static final String DEADLOCK = "40P01";
    static final String SERIALIZATION_FAILURE = "40001";

    private ReleaseErrors() {
    }

    static WebApplicationException badRequest(String messageKey) {
        return failure(400, messageKey);
    }

    static WebApplicationException notFound(String messageKey) {
        return failure(404, messageKey);
    }

    static WebApplicationException conflict() {
        return failure(409, "error.concurrent.update");
    }

    private static WebApplicationException failure(int status, String messageKey) {
        return new WebApplicationException(jakarta.ws.rs.core.Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Response.ERROR(messageKey))
                .build());
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
            throw conflict();
        }
        return Response.INTERNAL_ERROR();
    }
}
