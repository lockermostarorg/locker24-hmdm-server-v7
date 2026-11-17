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

import com.hmdm.rest.json.Response;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Singleton
@Path("/private/update")
public class UpdateResource {

    private static final String UPDATES_DISABLED = "error.updates.disabled";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/check")
    public Response checkUpdates() {
        return Response.ERROR(UPDATES_DISABLED);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response downloadUpdates() {
        return Response.ERROR(UPDATES_DISABLED);
    }
}
