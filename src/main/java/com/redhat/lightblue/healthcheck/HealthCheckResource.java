package com.redhat.lightblue.healthcheck;

import java.net.InetAddress;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.lightblue.client.LightblueClient;
import com.redhat.lightblue.client.Projection;
import com.redhat.lightblue.client.http.LightblueHttpClient;
import com.redhat.lightblue.client.request.data.DataInsertRequest;
import com.redhat.lightblue.healthcheck.model.Test;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/healthcheck")
public class HealthCheckResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckResource.class);

    private static final String ENTITY = "test";

    private final LightblueClient client;

    public HealthCheckResource() {
        client = new LightblueHttpClient();
    }

    @GET
    @Path("/check")
    public Response check() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            DataInsertRequest insert = new DataInsertRequest(ENTITY);
            insert.returns(Projection.includeField("_id"));
            insert.create(new Test(hostname, "inserted"));
            Test t = client.data(insert, Test.class);

            LOGGER.debug("Health check passed.");
            return Response.status(Status.ACCEPTED).entity("hi").build();
        } catch (Exception e) {
            LOGGER.error("Health check failed.", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

}
