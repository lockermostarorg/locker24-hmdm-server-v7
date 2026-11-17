/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.rest.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import jakarta.inject.Named;

import com.hmdm.notification.PushService;
import com.hmdm.persistence.*;
import com.hmdm.persistence.domain.*;
import com.hmdm.rest.json.*;
import com.hmdm.security.SecurityContext;
import com.hmdm.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hmdm.util.FileExistsException;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Application")
@SecurityRequirement(name = "Bearer Token")
@Singleton
@Path("/private/applications")
public class ApplicationResource {

    // A logging service
    private static final Logger logger  = LoggerFactory.getLogger(ApplicationResource.class);
    private File baseDirectory;
    private ApplicationDAO applicationDAO;
    private ConfigurationDAO configurationDAO;
    private UnsecureDAO unsecureDAO;
    private PushService pushService;

    /**
     * <p>A constructor required by Swagger.</p>
     */
    public ApplicationResource() {
    }

    @Inject
    public ApplicationResource(ApplicationDAO applicationDAO,
                               ConfigurationDAO configurationDAO,
                               UnsecureDAO unsecureDAO,
                               PushService pushService,
                               @Named("files.directory") String filesDirectory) {
        this.applicationDAO = applicationDAO;
        this.configurationDAO = configurationDAO;
        this.unsecureDAO = unsecureDAO;
        this.pushService = pushService;
        this.baseDirectory = new File(filesDirectory);

        if (!this.baseDirectory.exists()) {
            this.baseDirectory.mkdirs();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Get all applications",
            description = "Gets the list of all available applications"
    )
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = Application.class))
            )
    )
    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllApplications() {
        if (!SecurityContext.get().hasPermission("applications")) {
            logger.error("Unauthorized attempt to access application list by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        List<Application> applications = this.applicationDAO.getAllApplications();
        this.applicationDAO.setApplicationSizes(applications);
        return Response.OK(applications);
    }

 // =================================================================================================================
    @Operation(
            summary = "Search applications",
            description = "Search applications meeting the specified filter value"
    )
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = Application.class))
            )
    )
    @GET
    @Path("/search/{value}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchApplications(@PathParam("value") @Parameter(description = "A filter value") String value) {
        if (!SecurityContext.get().hasPermission("applications")) {
            logger.error("Unauthorized attempt to access application list by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        List<Application> applications = this.applicationDAO.getAllApplicationsByValue(value);
        this.applicationDAO.setApplicationSizes(applications);
        return Response.OK(applications);
    }

    // =================================================================================================================

    /**
     * <p>Gets the list of application ids/names matching the specified filter for autocompletions.</p>
     *
     * @param filter a filter to be used for filtering the records.
     * @return a response with list of devices matching the specified filter.
     */
    @Operation(summary = "Get app ids and names for autocompletions")
    @POST
    @Path("/autocomplete")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApplicationsForAutocomplete(String filter) {
        try {
            List<LookupItem> applications
                    = this.applicationDAO.getApplicationPkgLookup(filter, 10);
            return Response.OK(applications);
        } catch (Exception e) {
            logger.error("Failed to search the applications due to unexpected error. Filter: {}", filter, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Get application versions",
            description = "Gets the list of versions for specified application"
    )
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = ApplicationVersion.class))
            )
    )
    @GET
    @Path("/{id}/versions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllApplicationVersions(@PathParam("id") @Parameter(description = "Application ID") Integer id) {
        if (!SecurityContext.get().hasPermission("applications")) {
            logger.error("Unauthorized attempt to access application version list by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            List<ApplicationVersion> versions = this.applicationDAO.getApplicationVersions(id);
            this.applicationDAO.setApplicationVersionSizes(versions);
            return Response.OK(versions);
        } catch (Exception e) {
            logger.error("Failed to retrieve the versions for application #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Get application",
            description = "Gets the details for specified application"
    )
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = Application.class))
    )
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApplication(@PathParam("id")
                                   @Parameter(description = "Application ID", schema = @Schema(type = "integer"))
                                       Integer id) {
        if (!SecurityContext.get().hasPermission("applications")) {
            logger.error("Unauthorized attempt to access application list by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            return Response.OK(this.applicationDAO.findById(id));
        } catch (Exception e) {
            logger.error("Failed to retrieve the details for application #{}", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Create or update Android application",
            description = "Create a new Android application (if id is not provided) or update existing one otherwise."
    )
    @Path("/android")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateApplication(Application application) {
        if (!SecurityContext.get().hasPermission("edit_applications")) {
            logger.error("Unauthorized attempt to update application by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            if (application.getId() == null) {
                final int appId = this.applicationDAO.insertApplication(application);
                application = this.applicationDAO.findById(appId);
                return Response.OK(application);
            } else {
                this.applicationDAO.updateApplication(application);
                if (application.getUrl() != null && application.getLatestVersion() != null && !application.isSplit()) {
                    ApplicationVersion version = applicationDAO.findApplicationVersionById(application.getLatestVersion());
                    if (version != null) {
                        version.setUrl(application.getUrl());
                        applicationDAO.updateApplicationVersion(version);
                        logger.info("Application " + application.getPkg() + " updated to version " + version.getVersion() +
                                ", user " + SecurityContext.get().getCurrentUserName());
                    }
                }
                return Response.OK();
            }
        } catch (DuplicateApplicationException e) {
            logger.error("Failed to create or update application", e);
            return Response.DUPLICATE_APPLICATION();
        } catch (RecentApplicationVersionExistsException e) {
            logger.error("Failed to create or update application", e);
            return Response.RECENT_APPLICATION_VERSION_EXISTS();
        } catch (CommonAppAccessException e) {
            logger.error("Failed to create or update application", e);
            return Response.COMMON_APPLICATION_ACCESS_PROHIBITED();
        } catch (FileExistsException e) {
            logger.error("Failed to create or update application", e);
            return Response.FILE_EXISTS();
        } catch (Exception e) {
            logger.error("Failed to create or update application", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Create or update Web-page application",
            description = "Create a new Web-page application (if id is not provided) or update existing one otherwise."
    )
    @Path("/web")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateWebApplication(Application application) {
        if (!SecurityContext.get().hasPermission("edit_applications")) {
            logger.error("Unauthorized attempt to update application by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            if (application.getId() == null) {
                final int appId = this.applicationDAO.insertWebApplication(application);
                application = this.applicationDAO.findById(appId);
                return Response.OK(application);
            } else {
                // TODO : ISV : Handle the scenario for inserting new version for the same package here
                this.applicationDAO.updateWebApplication(application);
                if (application.getUrl() != null && application.getLatestVersion() != null) {
                    ApplicationVersion version = applicationDAO.findApplicationVersionById(application.getLatestVersion());
                    if (version != null) {
                        version.setUrl(application.getUrl());
                        applicationDAO.updateApplicationVersion(version);
                    }
                }

                return Response.OK();
            }
        } catch (DuplicateApplicationException e) {
            logger.error("Failed to create or update application", e);
            return Response.DUPLICATE_APPLICATION();
        } catch (RecentApplicationVersionExistsException e) {
            logger.error("Failed to create or update application", e);
            return Response.RECENT_APPLICATION_VERSION_EXISTS();
        } catch (CommonAppAccessException e) {
            logger.error("Failed to create or update application", e);
            return Response.COMMON_APPLICATION_ACCESS_PROHIBITED();
        } catch (FileExistsException e) {
            logger.error("Failed to create or update application", e);
            return Response.FILE_EXISTS();
        } catch (Exception e) {
            logger.error("Failed to create or update application", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Create or update application version",
            description = "Create a new application version (if id is not provided) or update existing one otherwise."
    )
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/versions")
    public Response updateApplicationVersion(ApplicationVersion applicationVersion) {
        if (!SecurityContext.get().hasPermission("edit_application_versions")) {
            logger.error("Unauthorized attempt to update application version by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            if (applicationVersion.getId() == null) {
                final String requestedMode;
                try {
                    requestedMode = FollowLatestRules.normalizeUploadMode(applicationVersion.getReleaseMode());
                } catch (IllegalArgumentException e) {
                    return Response.ERROR("error.release.mode.invalid");
                }
                final Integer userId = SecurityContext.get().getCurrentUser().map(User::getId).orElse(null);
                // Here only "url" is coming, we may need to change it to urlArmeabi or urlArm64 if arch is set
                final List<Integer> moved = this.applicationDAO.insertApplicationVersionWithRelease(
                        applicationVersion, requestedMode, userId);
                final ApplicationVersion saved = this.applicationDAO.findApplicationVersionById(applicationVersion.getId());
                final String mode = effectiveMode(saved, requestedMode);
                return Response.OK(releaseAndNotify(saved, mode, moved));
            } else {
                logger.info("Application " + applicationVersion.getApplicationId() + " version updated: " + applicationVersion.getVersion() +
                        ", user " + SecurityContext.get().getCurrentUserName());
                this.applicationDAO.updateApplicationVersion(applicationVersion);
                return Response.OK();
            }
        } catch (DuplicateApplicationException e) {
            logger.error("Failed to create or update application version", e);
            return Response.DUPLICATE_APPLICATION();
        } catch (RecentApplicationVersionExistsException e) {
            logger.error("Failed to create or update application version", e);
            return Response.RECENT_APPLICATION_VERSION_EXISTS();
        } catch (ApplicationVersionPackageMismatchException e) {
            logger.error("Failed to create or update application version", e);

            Map<String, String> args = new HashMap<>();
            args.put("expected", e.getExpectedPackageName());
            args.put("actual", e.getActualPackageName());

            return Response.ERROR("error.application.version.pkg.mismatch", args);
        } catch (VersionExistsException e) {
            logger.error("Refused to create application version", e);
            return Response.ERROR("error.version.exists");
        } catch (CommonAppAccessException e) {
            logger.error("Failed to create or update application version", e);
            return Response.COMMON_APPLICATION_ACCESS_PROHIBITED();
        } catch (SecurityException e) {
            logger.error("Prohibited to create or update application version by current user", e);
            return Response.PERMISSION_DENIED();
        } catch (FileExistsException e) {
            logger.error("Failed to create or update application version", e);
            return Response.FILE_EXISTS();
        } catch (Exception e) {
            logger.error("Failed to create or update application version", e);
            return ReleaseErrors.unexpected(e);
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Delete application",
            description = "Delete an existing application"
    )
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeApplication(@PathParam("id")
                                      @Parameter(description = "Application ID", schema = @Schema(type = "integer"))
                                          Integer id) {
        if (!SecurityContext.get().hasPermission("edit_applications")) {
            logger.error("Unauthorized attempt to remove application by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            Application application = applicationDAO.findById(id);
            if (application != null) {
                logger.info("Application '{}' ({}) is removed by user {}",
                        application.getName(), application.getPkg(), SecurityContext.get().getCurrentUserName());
                this.applicationDAO.removeApplicationById(id, true);
                return Response.OK();
            } else {
                logger.error("Cannot find application by id #{}", id);
                return Response.INTERNAL_ERROR();
            }
        } catch (SecurityException e) {
            logger.error("Prohibited to delete application #{} by current user", id, e);
            return Response.PERMISSION_DENIED();
        } catch (ApplicationReferenceExistsException e) {
            logger.error("Prohibited to delete application #{} as it is still referenced in configurations", id, e);
            return Response.APPLICATION_CONFIG_REFERENCE_EXISTS();
        } catch (Exception e) {
            logger.error("Failed to delete application #{} due to unexpected error", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Delete application version",
            description = "Delete an existing application version"
    )
    @DELETE
    @Path("/versions/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeApplicationVersion(@PathParam("id")
                                             @Parameter(description = "Application Version ID", schema = @Schema(type = "integer"))
                                                 Integer id) {
        if (!SecurityContext.get().hasPermission("edit_application_versions")) {
            logger.error("Unauthorized attempt to delete application version by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            ApplicationVersion version = applicationDAO.findApplicationVersionById(id);
            if (version != null) {
                Application application = applicationDAO.findById(version.getApplicationId());
                if (application != null) {
                    logger.info("Version {} of application '{}' ({}) is removed by user {}",
                            version.getVersion(), application.getName(), application.getPkg(),
                            SecurityContext.get().getCurrentUserName());
                } else {
                    logger.info("Version {} of unexisting application {} is removed by user {}",
                            version.getVersion(), version.getApplicationId(), SecurityContext.get().getCurrentUserName());
                }
                this.applicationDAO.removeApplicationVersionByIdWithAPKFile(id);
                return Response.OK();
            } else {
                logger.error("Cannot find application version by id #{}", id);
                return Response.INTERNAL_ERROR();
            }
        } catch (SecurityException e) {
            logger.error("Prohibited to delete application version #{} by current user", id, e);
            return Response.PERMISSION_DENIED();
        } catch (LastReleasedVersionException e) {
            logger.error("Prohibited to delete the last released version #{}", id, e);
            return Response.ERROR("error.application.version.last.released");
        } catch (ApplicationReferenceExistsException e) {
            logger.error("Prohibited to delete application version #{} as it is still referenced in configurations", id, e);
            return Response.APPLICATION_CONFIG_REFERENCE_EXISTS();
        } catch (Exception e) {
            logger.error("Failed to delete application version #{} due to unexpected error", id, e);
            return ReleaseErrors.unexpected(e);
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Release application version",
            description = "Releases a held application version: configurations following the latest version move to it"
    )
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/versions/{id}/release")
    public Response releaseApplicationVersion(@PathParam("id")
                                              @Parameter(description = "Application Version ID", schema = @Schema(type = "integer"))
                                                      Integer id,
                                              ReleaseVersionRequest request) {
        if (!SecurityContext.get().hasPermission("edit_application_versions")) {
            logger.error("Unauthorized attempt to release application version by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        final String mode = request == null ? null : request.getMode();
        if (!FollowLatestRules.MODE_SCHEDULE.equals(mode) && !FollowLatestRules.MODE_IMMEDIATE.equals(mode)) {
            return Response.ERROR("error.release.mode.invalid");
        }
        try {
            final ApplicationVersion version = id == null ? null : applicationDAO.findApplicationVersionById(id);
            if (version == null) {
                return Response.OBJECT_NOT_FOUND_ERROR();
            }
            applicationDAO.checkVersionWriteAccess(version.getApplicationId());
            if (!FollowLatestRules.STATE_HELD.equals(version.getReleaseState())) {
                return Response.ERROR("error.version.already.released");
            }
            final User user = SecurityContext.get().getCurrentUser().get();
            final List<Integer> moved = applicationDAO.releaseVersion(id, mode, user.getId());
            return Response.OK(releaseAndNotify(version, mode, moved));
        } catch (CommonAppAccessException e) {
            logger.error("Prohibited to release application version #{} by current user", id, e);
            return Response.COMMON_APPLICATION_ACCESS_PROHIBITED();
        } catch (SecurityException e) {
            logger.error("Prohibited to release application version #{} by current user", id, e);
            return Response.PERMISSION_DENIED();
        } catch (Exception e) {
            logger.error("Failed to release application version #{}", id, e);
            return ReleaseErrors.unexpected(e);
        }
    }

    private static String effectiveMode(ApplicationVersion saved, String requestedMode) {
        if (saved != null && FollowLatestRules.STATE_HELD.equals(saved.getReleaseState())) {
            return FollowLatestRules.MODE_HOLD;
        }
        return FollowLatestRules.MODE_HOLD.equals(requestedMode) ? FollowLatestRules.MODE_SCHEDULE : requestedMode;
    }

    private ReleaseSummary releaseAndNotify(ApplicationVersion version, String mode, List<Integer> moved) {
        int devices = 0;
        int failed = 0;
        final Application application = version == null ? null : applicationDAO.findById(version.getApplicationId());
        if (moved != null && !FollowLatestRules.MODE_HOLD.equals(mode)) {
            for (Integer configurationId : moved) {
                try {
                    devices += this.pushService.notifyAllDevicesOnUpdate(configurationId);
                    if (FollowLatestRules.MODE_IMMEDIATE.equals(mode)) {
                        final String payload = PushService.forceUpdatePayload(
                                application == null ? null : application.getPkg(),
                                version == null ? null : version.getVersion(), System.currentTimeMillis());
                        this.pushService.notifyAllDevicesOnForceUpdate(configurationId, payload);
                    }
                } catch (Exception e) {
                    failed++;
                    logger.error("Failed to notify devices of configuration #{} on release", configurationId, e);
                }
            }
        }
        final int configurations = moved == null ? 0 : moved.size();
        boolean notLatest = false;
        if (!FollowLatestRules.MODE_HOLD.equals(mode)) {
            notLatest = application != null && version != null && application.getLatestVersion() != null
                    && version.getId() != null && !application.getLatestVersion().equals(version.getId());
            logger.info("Release app={} version={} mode={} by={} configurations={} devices={} configurationIds={}",
                    application == null ? null : application.getPkg(), version == null ? null : version.getVersion(),
                    mode, SecurityContext.get().getCurrentUserName(), configurations, devices, moved);
        }
        return new ReleaseSummary(configurations, devices, mode, failed, notLatest);
    }

    // =================================================================================================================
    @Operation(
            summary = "Notify tablets of an application version again",
            description = "Re-sends the update push (and the install-now push for immediate) to every configuration currently on the version; moves nothing"
    )
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/versions/{id}/notify")
    public Response notifyApplicationVersion(@PathParam("id")
                                             @Parameter(description = "Application Version ID", schema = @Schema(type = "integer"))
                                                     Integer id,
                                             ReleaseVersionRequest request) {
        if (!SecurityContext.get().hasPermission("edit_application_versions")) {
            logger.error("Unauthorized attempt to notify about application version by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        final String mode = request == null ? null : request.getMode();
        if (!FollowLatestRules.MODE_SCHEDULE.equals(mode) && !FollowLatestRules.MODE_IMMEDIATE.equals(mode)) {
            return Response.ERROR("error.release.mode.invalid");
        }
        try {
            final ApplicationVersion version = id == null ? null : applicationDAO.findApplicationVersionById(id);
            if (version == null) {
                return Response.OBJECT_NOT_FOUND_ERROR();
            }
            final List<Integer> configurations = applicationDAO.getConfigurationsOnVersion(id);
            return Response.OK(releaseAndNotify(version, mode, configurations));
        } catch (CommonAppAccessException e) {
            logger.error("Prohibited to notify about application version #{} by current user", id, e);
            return Response.COMMON_APPLICATION_ACCESS_PROHIBITED();
        } catch (SecurityException e) {
            logger.error("Prohibited to notify about application version #{} by current user", id, e);
            return Response.PERMISSION_DENIED();
        } catch (Exception e) {
            logger.error("Failed to notify about application version #{}", id, e);
            return ReleaseErrors.unexpected(e);
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Get application configurations",
            description = "Gets the list of configurations using requested application"
    )
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = ApplicationConfigurationLink.class))
            )
    )
    @GET
    @Path("/configurations/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApplicationConfigurations(@PathParam("id")
                                                 @Parameter(description = "Application ID", schema = @Schema(type = "integer"))
                                                     Integer id) {
        if (!SecurityContext.get().hasPermission("applications")) {
            logger.error("Unauthorized attempt to get application configurations by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        return Response.OK(this.applicationDAO.getApplicationConfigurations(id));
    }

    // =================================================================================================================
    @Operation(
            summary = "Get application version configurations",
            description = "Gets the list of configurations using requested application version"
    )
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = ApplicationConfigurationLink.class))
            )
    )
    @GET
    @Path("/version/{id}/configurations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApplicationVersionConfigurations(
            @PathParam("id")
            @Parameter(description = "Application Version ID", schema = @Schema(type = "integer"))
            Integer id
    ) {
        if (!SecurityContext.get().hasPermission("applications")) {
            logger.error("Unauthorized attempt to get application version configurations by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        try {
            return Response.OK(this.applicationDAO.getApplicationVersionConfigurations(id));
        } catch (Exception e) {
            logger.error("Failed to get list of application version configurations", e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Update application configurations",
            description = "Updates the list of configurations using requested application"
    )
    @POST
    @Path("/configurations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateApplicationConfigurations(LinkConfigurationsToAppRequest request) {
        if (!SecurityContext.get().hasPermission("edit_applications")) {
            logger.error("Unauthorized attempt to update application configurations by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        if (request == null || request.getApplicationId() == 0 || request.getConfigurations() == null) {
            return Response.ERROR("error.application.id.required");
        }
        try {
            User user = SecurityContext.get().getCurrentUser().get();
            if (!user.isAllConfigAvailable()) {
                // Remove all configurations unavailable to user
                request.getConfigurations().removeIf(c ->
                        user.getConfigurations().stream().filter(uc -> uc.getId() == c.getConfigurationId()).findFirst() == null);
            }
            Integer _masterCustomer = unsecureDAO.getMasterCustomerId();
            final int masterCustomer = _masterCustomer != null ? _masterCustomer : 0;

            // Avoid access to objects of another customer
            request.getConfigurations().removeIf(c -> {
                // findById will raise a SecurityException if attempting to access an object of another customer
                // So actually this code is a bit redundant, but it guards access to own objects anyway
                Application application = applicationDAO.findById(c.getApplicationId());
                Configuration configuration = configurationDAO.getConfigurationById(c.getConfigurationId());
                return (application.getCustomerId() != masterCustomer && application.getCustomerId() != user.getCustomerId()) ||
                       configuration.getCustomerId() != user.getCustomerId();
            });
            logger.info("Application configurations updated by user " + SecurityContext.get().getCurrentUserName());
            this.applicationDAO.updateApplicationConfigurations(request);

            for (ApplicationConfigurationLink configurationLink : request.getConfigurations()) {
                if (configurationLink.isNotify()) {
                    this.pushService.notifyDevicesOnUpdate(configurationLink.getConfigurationId());
                }
            }

            return Response.OK();
        } catch (Exception e) {
            logger.error("Unexpected error when updating application configurations", e);
            return ReleaseErrors.unexpected(e);
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Update application version configurations",
            description = "Updates the list of configurations using requested application version"
    )
    @POST
    @Path("/version/configurations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateApplicationVersionConfigurations(LinkConfigurationsToAppVersionRequest request) {
        if (!SecurityContext.get().hasPermission("edit_application_versions")) {
            logger.error("Unauthorized attempt to update application version configurations by user " +
                    SecurityContext.get().getCurrentUserName());
            return Response.PERMISSION_DENIED();
        }
        if (request == null || request.getApplicationVersionId() == 0 || request.getConfigurations() == null) {
            return Response.ERROR("error.application.version.id.required");
        }
        try {
            User user = SecurityContext.get().getCurrentUser().get();
            if (!user.isAllConfigAvailable()) {
                // Remove all configurations unavailable to user
                request.getConfigurations().removeIf(c ->
                        user.getConfigurations().stream().filter(uc -> uc.getId() == c.getConfigurationId()).findFirst() == null);
            }
            logger.info("Application version configurations updated by user " +
                    SecurityContext.get().getCurrentUserName());
            this.applicationDAO.updateApplicationVersionConfigurations(request, user);
            for (ApplicationVersionConfigurationLink configurationLink : request.getConfigurations()) {
                if (configurationLink.isNotify()) {
                    this.pushService.notifyDevicesOnUpdate(configurationLink.getConfigurationId());
                }
            }

            return Response.OK();
        } catch (Exception e) {
            logger.error("Unexpected error when updating application configurations", e);
            return ReleaseErrors.unexpected(e);
        }
    }

    @Operation(summary = "", hidden = true)
    @GET
    @Path("/admin/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAdminApplications() {
        return Response.OK(this.applicationDAO.getAllAdminApplications());
    }

    @Operation(summary = "", hidden = true)
    @GET
    @Path("/admin/search/{value}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchAdminApplications(@PathParam("value") String value) {
        return Response.OK(this.applicationDAO.getAllAdminApplicationsByValue(value));
    }

    @Operation(summary = "", hidden = true)
    @GET
    @Path("/admin/common/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response turnApplicationIntoCommon(@PathParam("id") Integer id) {
        try {
            logger.info("Turn application into common: " + id);
            this.applicationDAO.turnApplicationIntoCommon(id);
            return Response.OK();
        } catch (DuplicateApplicationException e) {
            logger.error("Failed to turn application with ID: {} into common", id, e);
            return Response.DUPLICATE_APPLICATION();
        } catch (Exception e) {
            logger.error("Failed to turn application with ID: {} into common", id, e);
            return Response.INTERNAL_ERROR();
        }
    }

    // =================================================================================================================
    @Operation(
            summary = "Validate application package",
            description = "Validate the application package ID for uniqueness"
    )
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    array = @ArraySchema(schema = @Schema(implementation = Application.class))
            )
    )
    @Path("/validatePkg")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validateApplication(Application application) {
        try {
            final List<Application> sameNameApps = this.applicationDAO.getApplicationsForName(application);
            for (Application app : sameNameApps) {
                if (!app.getPkg().equalsIgnoreCase(application.getPkg())) {
                    return Response.ERROR("error.app.name.exists");
                }
            }
            final List<Application> otherApps = this.applicationDAO.getApplicationsForPackageID(application);
            return Response.OK(otherApps);
        } catch (Exception e) {
            logger.error("Failed to validate application", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
