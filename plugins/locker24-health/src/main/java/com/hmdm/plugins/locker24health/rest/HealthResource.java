package com.hmdm.plugins.locker24health.rest;

import com.hmdm.persistence.domain.User;
import com.hmdm.plugins.locker24health.HealthPluginConfigurationImpl;
import com.hmdm.plugins.locker24health.history.OfflinePeriods;
import com.hmdm.plugins.locker24health.persistence.HealthDAO;
import com.hmdm.plugins.locker24health.persistence.domain.GapRow;
import com.hmdm.plugins.locker24health.persistence.domain.HistoryRow;
import com.hmdm.plugins.locker24health.persistence.domain.ReceivedBounds;
import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import com.hmdm.plugins.locker24health.rest.json.FleetResponse;
import com.hmdm.plugins.locker24health.rest.json.FleetRow;
import com.hmdm.plugins.locker24health.rest.json.HealthSettings;
import com.hmdm.plugins.locker24health.rest.json.HistoryResponse;
import com.hmdm.plugins.locker24health.settings.SettingsValidator;
import com.hmdm.plugins.locker24health.status.StatusEvaluator;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

@Singleton
@Path("/private/plugin-locker24-health")
@Tag(name = "Fleet health plugin")
public class HealthResource {

    public static final String PERMISSION = "plugin_locker24_health_access";
    public static final String SETTINGS_PERMISSION = "settings";
    public static final int MAX_POINTS = 1000;
    public static final int DEFAULT_HOURS = 24;
    public static final int MAX_HOURS = 720;
    static final String ERROR_VALIDATION = "error.validation";
    private static final long HOUR_MS = 3_600_000L;
    private static final List<String> STATUSES = Arrays.asList("green", "yellow", "red", "unknown");

    private static final Logger logger = LoggerFactory.getLogger(HealthResource.class);

    private final HealthDAO dao;
    private final LongSupplier clock;

    @Inject
    public HealthResource(HealthDAO dao) {
        this(dao, System::currentTimeMillis);
    }

    HealthResource(HealthDAO dao, LongSupplier clock) {
        this.dao = dao;
        this.clock = clock;
    }

    @Operation(summary = "Fleet health", security = @SecurityRequirement(name = "Bearer Token"))
    @GET
    @Path("/devices")
    @Produces(MediaType.APPLICATION_JSON)
    public Response devices(@QueryParam("configurationId") Integer configurationId,
                            @QueryParam("status") String status,
                            @QueryParam("model") String model) {
        if (!allowed(PERMISSION)) {
            return Response.PERMISSION_DENIED();
        }
        String statusFilter = blankToNull(status);
        if (statusFilter != null && !STATUSES.contains(statusFilter)) {
            return Response.ERROR(ERROR_VALIDATION);
        }
        try {
            User user = user();
            List<FleetRow> rows = dao.loadFleet(user.getCustomerId(), user.getId(), configurationId,
                    statusFilter, blankToNull(model));
            FleetResponse out = new FleetResponse();
            out.setTruncated(rows.size() > HealthMapper.FLEET_LIMIT);
            out.setDevices(out.isTruncated() ? new ArrayList<>(rows.subList(0, HealthMapper.FLEET_LIMIT)) : rows);
            return Response.OK(out);
        } catch (Exception e) {
            return failure("fleet", e);
        }
    }

    @Operation(summary = "Device health history", security = @SecurityRequirement(name = "Bearer Token"))
    @GET
    @Path("/devices/{deviceId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response history(@PathParam("deviceId") int deviceId, @QueryParam("hours") Integer hours) {
        if (!allowed(PERMISSION)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            User user = user();
            if (!dao.canAccessDevice(user.getCustomerId(), user.getId(), deviceId)) {
                return Response.PERMISSION_DENIED();
            }
            int h = hours == null ? DEFAULT_HOURS : Math.max(1, Math.min(MAX_HOURS, hours));
            long to = clock.getAsLong();
            long from = to - h * HOUR_MS;

            long bucket = Math.max(1L, (to - from + MAX_POINTS - 1) / MAX_POINTS);
            List<HistoryRow> rows = dao.historyPoints(deviceId, from, to, bucket);
            boolean truncated = rows.size() > HealthMapper.POINT_LIMIT;
            if (truncated) {
                rows = rows.subList(rows.size() - HealthMapper.POINT_LIMIT, rows.size());
            }

            HistoryResponse out = new HistoryResponse();
            out.setFrom(from);
            out.setTo(to);
            out.setBucketMs(bucket);
            out.setPoints(points(rows));

            List<GapRow> gaps = dao.offlineGaps(deviceId, from, to, StatusEvaluator.OFFLINE_AFTER_MS);
            if (gaps.size() > HealthMapper.GAP_LIMIT) {
                truncated = true;
                gaps = gaps.subList(0, HealthMapper.GAP_LIMIT);
            }
            ReceivedBounds bounds = dao.receivedBounds(deviceId, from, to);
            Long before = dao.lastReceivedBefore(deviceId, from);
            Long first = bounds == null ? null : bounds.getFirst();
            Long last = bounds == null ? null : bounds.getLast();
            Long latest = before == null && first == null ? dao.latestReceived(deviceId) : null;
            out.setOfflinePeriods(OfflinePeriods.assemble(gaps, before, first, last, latest, from, to,
                    StatusEvaluator.OFFLINE_AFTER_MS));
            out.setTruncated(truncated);
            return Response.OK(out);
        } catch (Exception e) {
            return failure("history", e);
        }
    }

    private static List<HistoryResponse.Point> points(List<HistoryRow> rows) {
        List<HistoryResponse.Point> out = new ArrayList<>(rows.size());
        for (HistoryRow r : rows) {
            HistoryResponse.Point p = new HistoryResponse.Point();
            p.setT(r.getMeasuredAt());
            p.setBatteryPct(r.getBatteryPct());
            p.setTemperatureC(r.getTemperatureC());
            p.setRssiDbm(r.getRssiDbm());
            p.setCharging(r.getCharging());
            p.setKioskRunning(r.getKioskRunning());
            out.add(p);
        }
        return out;
    }

    @Operation(summary = "Health settings", security = @SecurityRequirement(name = "Bearer Token"))
    @GET
    @Path("/settings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSettings() {
        if (!allowed(PERMISSION)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            HealthSettings stored = dao.loadSettings(user().getCustomerId());
            HealthSettings out = new HealthSettings();
            out.setWebhookUrlSet(stored != null && Boolean.TRUE.equals(stored.getWebhookUrlSet()));
            out.setTimeZone(stored != null && stored.getTimeZone() != null
                    ? stored.getTimeZone() : HealthPluginConfigurationImpl.DEFAULT_ZONE.getId());
            out.setEditable(allowed(SETTINGS_PERMISSION));
            return Response.OK(out);
        } catch (Exception e) {
            return failure("settings read", e);
        }
    }

    @Operation(summary = "Save health settings", security = @SecurityRequirement(name = "Bearer Token"))
    @PUT
    @Path("/settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveSettings(HealthSettings settings) {
        if (!allowed(PERMISSION) || !allowed(SETTINGS_PERMISSION)) {
            return Response.PERMISSION_DENIED();
        }
        if (settings == null) {
            return Response.ERROR(ERROR_VALIDATION);
        }
        try {
            SettingsValidator.validate(settings);
        } catch (IllegalArgumentException e) {
            return Response.ERROR(ERROR_VALIDATION);
        }
        try {
            int customerId = user().getCustomerId();
            dao.saveSettings(customerId, settings);
            return getSettings();
        } catch (Exception e) {
            return failure("settings write", e);
        }
    }

    private static boolean allowed(String permission) {
        SecurityContext ctx = SecurityContext.get();
        return ctx != null && ctx.getCurrentUser().isPresent() && ctx.hasPermission(permission);
    }

    private static User user() {
        Optional<User> u = SecurityContext.get().getCurrentUser();
        return u.get();
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static Response failure(String what, Exception e) {
        logger.error("Health panel request failed ({}): {}", what, e.getClass().getName());
        return Response.INTERNAL_ERROR();
    }
}
