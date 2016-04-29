package com.redhat.lightblue.healthcheck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
import com.redhat.lightblue.client.LightblueException;
import com.redhat.lightblue.client.Projection;
import com.redhat.lightblue.client.Query;
import com.redhat.lightblue.client.Update;
import com.redhat.lightblue.client.http.LightblueHttpClient;
import com.redhat.lightblue.client.request.data.DataDeleteRequest;
import com.redhat.lightblue.client.request.data.DataFindRequest;
import com.redhat.lightblue.client.request.data.DataInsertRequest;
import com.redhat.lightblue.client.request.data.DataUpdateRequest;
import com.redhat.lightblue.healthcheck.model.Test;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/healthcheck")
public class HealthCheckResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckResource.class);

    private static final String ENTITY = "test";

    private final LightblueClient client;
    private final String hostname;

    public HealthCheckResource() throws Exception {
        client = new LightblueHttpClient();
        hostname = InetAddress.getLocalHost().getHostName();
    }

    @GET
    @Path("/check")
    public Response check() {
        try {
            DataInsertRequest insertRequest = new DataInsertRequest(ENTITY);
            insertRequest.returns(Projection.includeField("_id"));
            insertRequest.create(new Test(hostname, "created"));
            Test created = client.data(insertRequest, Test.class);

            assertNotNull(created);
            String uuid = created.get_id();
            assertNotNull(uuid);

            Test found = find("_id", uuid);

            assertNotNull(found);
            assertEquals(hostname, found.getHostname());
            assertEquals("created", found.getValue());

            DataUpdateRequest updateRequest = new DataUpdateRequest(ENTITY);
            updateRequest.where(Query.withValue("_id", Query.eq, uuid));
            updateRequest.returns(Projection.excludeFieldRecursively("*"));
            updateRequest.updates(Update.set("value", "updated"));
            client.data(updateRequest);

            found = find("hostname", hostname);

            assertNotNull(found);
            assertEquals(hostname, found.getHostname());
            assertEquals("updated", found.getValue());

            DataDeleteRequest deleteRequest = new DataDeleteRequest(ENTITY);
            deleteRequest.where(Query.withValue("_id", Query.eq, uuid));
            client.data(deleteRequest);

            assertNull(find("_id", uuid));

            LOGGER.debug("Health check passed.");
            return Response.status(Status.ACCEPTED).entity("{\"status\":\"success\"}").build();
        } catch (Exception e) {
            LOGGER.error("Health check failed.", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("{\"status\":\"error\"}").build();
        }
    }

    private Test find(String field, String uuid) throws LightblueException {
        DataFindRequest findRequest = new DataFindRequest(ENTITY);
        findRequest.select(Projection.includeFieldRecursively("*"));
        findRequest.where(Query.withValue(field, Query.eq, uuid));
        return client.data(findRequest, Test.class);
    }

}
