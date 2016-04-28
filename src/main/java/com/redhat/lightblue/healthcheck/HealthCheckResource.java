package com.redhat.lightblue.healthcheck;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/healthcheck")
public class HealthCheckResource {

    @GET
    @Path("/check")
    public Response check() {
        return Response.status(Status.ACCEPTED).entity("hi").build();
    }

}
