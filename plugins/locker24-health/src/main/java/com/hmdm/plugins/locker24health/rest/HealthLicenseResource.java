package com.hmdm.plugins.locker24health.rest;

import com.hmdm.plugin.rest.json.LicenseResponse;
import com.hmdm.rest.json.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Singleton
@Path("/private/plugin-locker24-health/license")
@Tag(name = "Fleet health plugin license")
public class HealthLicenseResource {

    @Inject
    public HealthLicenseResource() {
    }

    @Operation(
            summary = "Checks for a plugin license",
            description = "Checks for valid license for a plugin",
            security = @SecurityRequirement(name = "Bearer Token")
    )
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response license() {
        return Response.OK(LicenseResponse.genericValidResponse());
    }
}
