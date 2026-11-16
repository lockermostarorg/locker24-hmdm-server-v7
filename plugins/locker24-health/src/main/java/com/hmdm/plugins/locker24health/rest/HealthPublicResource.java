package com.hmdm.plugins.locker24health.rest;

import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.plugin.service.PluginStatusCache;
import com.hmdm.plugins.locker24health.HealthPluginConfigurationImpl;
import com.hmdm.plugins.locker24health.ingest.IngestService;
import com.hmdm.plugins.locker24health.ingest.ReportParser;
import com.hmdm.plugins.locker24health.ingest.ValidationException;
import com.hmdm.plugins.locker24health.model.ParsedBatch;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import com.hmdm.util.CryptoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Singleton
@Path("/public/plugin-locker24-health")
@Tag(name = "Fleet health plugin public resource")
public class HealthPublicResource {

    public static final int MAX_BODY_BYTES = 262_144;
    static final String SIGNATURE_HEADER = "X-Request-Signature";
    static final String ERROR_SIGNATURE = "error.signature";
    static final String ERROR_VALIDATION = "error.validation";

    private static final Logger logger = LoggerFactory.getLogger(HealthPublicResource.class);

    private final UnsecureDAO unsecureDAO;
    private final PluginStatusCache pluginStatusCache;
    private final IngestService ingestService;
    private final String hashSecret;

    @Inject
    public HealthPublicResource(UnsecureDAO unsecureDAO,
                                PluginStatusCache pluginStatusCache,
                                IngestService ingestService,
                                @Named("hash.secret") String hashSecret) {
        this.unsecureDAO = unsecureDAO;
        this.pluginStatusCache = pluginStatusCache;
        this.ingestService = ingestService;
        this.hashSecret = hashSecret;
    }

    @Operation(summary = "Upload health reports", description = "Stores a signed batch of device health reports")
    @POST
    @Path("/report/{deviceNumber}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response report(@PathParam("deviceNumber") String deviceNumber,
                           @HeaderParam(SIGNATURE_HEADER) String signature,
                           @HeaderParam(HttpHeaders.CONTENT_LENGTH) String contentLength,
                           InputStream body) {
        String number = printable(deviceNumber);
        try {
            Device device = unsecureDAO.getDeviceByNumber(deviceNumber);
            if (device == null) {
                logger.warn("Health report from unknown device {}", number);
                return Response.DEVICE_NOT_FOUND_ERROR();
            }
            if (!CryptoUtil.checkRequestSignature(signature, hashSecret + deviceNumber)) {
                logger.warn("Health report from device {} rejected: bad signature", number);
                return Response.ERROR(ERROR_SIGNATURE);
            }

            SecurityContext.init(device.getCustomerId());
            try {
                if (pluginStatusCache.isPluginDisabled(HealthPluginConfigurationImpl.PLUGIN_ID)) {
                    logger.warn("Health report from device {} rejected: plugin disabled", number);
                    return Response.PLUGIN_DISABLED();
                }
                String json = readBounded(contentLength, body);
                if (json == null) {
                    logger.warn("Health report from device {} rejected: body over {} bytes", number, MAX_BODY_BYTES);
                    return Response.ERROR(ERROR_VALIDATION);
                }
                ParsedBatch batch;
                try {
                    batch = ReportParser.parse(json);
                } catch (ValidationException e) {
                    logger.warn("Health report from device {} rejected: invalid batch", number);
                    return Response.ERROR(ERROR_VALIDATION);
                }
                int stored = ingestService.store(device, batch, System.currentTimeMillis());
                logger.info("Health reports from device {}: {} received, {} new", number, batch.getReports().size(), stored);
                return Response.OK(Collections.singletonMap("accepted", stored));
            } finally {
                SecurityContext.release();
            }
        } catch (Exception e) {
            logger.error("Failed to store health reports from device {}", number, e);
            return Response.INTERNAL_ERROR();
        }
    }

    static String readBounded(String contentLength, InputStream body) throws IOException {
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength.trim()) > MAX_BODY_BYTES) {
                    return null;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (body == null) {
            return "";
        }
        byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String printable(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder(Math.min(s.length(), 64));
        for (int i = 0; i < s.length() && b.length() < 64; i++) {
            char c = s.charAt(i);
            b.append(c < 0x20 || c == 0x7F ? '?' : c);
        }
        return b.toString();
    }
}
