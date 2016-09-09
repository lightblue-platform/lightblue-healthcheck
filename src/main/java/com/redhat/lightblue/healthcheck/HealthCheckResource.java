package com.redhat.lightblue.healthcheck;

import com.redhat.lightblue.client.LightblueClient;
import com.redhat.lightblue.client.LightblueException;
import com.redhat.lightblue.client.MongoExecution;
import com.redhat.lightblue.client.MongoExecution.ReadPreference;
import com.redhat.lightblue.client.Projection;
import com.redhat.lightblue.client.Query;
import com.redhat.lightblue.client.Update;
import com.redhat.lightblue.client.http.LightblueHttpClient;
import com.redhat.lightblue.client.request.data.DataDeleteRequest;
import com.redhat.lightblue.client.request.data.DataFindRequest;
import com.redhat.lightblue.client.request.data.DataInsertRequest;
import com.redhat.lightblue.client.request.data.DataUpdateRequest;
import com.redhat.lightblue.healthcheck.model.HealthCheck;
import com.redhat.lightblue.healthcheck.model.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Path("/")
public class HealthCheckResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckResource.class);

    private static final String ENTITY = "test";

    private static final String ERROR_RESPONSE_DELIMITER = " , ";

    private final Map<String, LightblueClient> lightblueClients;
    private final String hostname;

    public HealthCheckResource() throws Exception {

        lightblueClients = new LinkedHashMap<>();

        //this config file contains one line for each lightblue client properties file that
        // should be read in and used.
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream("lightblue-clients.config");
        if(null != stream) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            String clientConfigFilePath;
            while ((clientConfigFilePath = reader.readLine()) != null) {
                lightblueClients.put(
                        clientConfigFilePath.replace(".properties", ""),
                        new LightblueHttpClient(clientConfigFilePath)
                );
            }
        } else {
            lightblueClients.put("lightblue-client", new LightblueHttpClient());
        }

        hostname = InetAddress.getLocalHost().getHostName();
    }

    @GET
    @Path("/health")
    public Response health() {

        boolean oneOrMoreHealthChecksFailed = false;
        List<HealthCheck> healthChecks = new ArrayList<>();

        for (Map.Entry<String,LightblueClient> client :  lightblueClients.entrySet()) {

            HealthCheck healthCheck = checkHealth(client.getKey(), client.getValue());

            if(healthCheck.failed()) {
                oneOrMoreHealthChecksFailed = true;
            }
            healthChecks.add(healthCheck);
        }

        return (oneOrMoreHealthChecksFailed ? getErrorResponse(healthChecks) : getSuccessResponse());

    }

    private Response getSuccessResponse() {
        return Response.status(Status.OK).entity(
                "{\"status\":\"success\"}"
        ).build();
    }

    private Response getErrorResponse(List<HealthCheck> healthChecks) {
        StringBuffer errorMessages = new StringBuffer();
        for(HealthCheck healthCheck : healthChecks) {
            errorMessages.append(healthCheck.getMessage());
            if(!errorMessages.toString().equals(healthCheck.getMessage())) {
                errorMessages.append(ERROR_RESPONSE_DELIMITER);
            }
        }
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(
                "{\"status\":\"error\",\"message\":\"" + errorMessages.toString() + "\"}"
        ).build();
    }

    private HealthCheck checkHealth(String clientName, LightblueClient client) {

        try {
            DataInsertRequest insertRequest = new DataInsertRequest(ENTITY);
            insertRequest.returns(Projection.includeField("_id"));
            insertRequest.create(new Test(hostname, "created"));
            Test created = client.data(insertRequest, Test.class);

            assertNotNull(created);
            String uuid = created.get_id();
            assertNotNull(uuid);

            Test found = find(client, uuid);

            assertNotNull(found);
            assertEquals(hostname, found.getHostname());
            assertEquals("created", found.getValue());

            DataUpdateRequest updateRequest = new DataUpdateRequest(ENTITY);
            updateRequest.where(Query.withValue("_id", Query.eq, uuid));
            updateRequest.returns(Projection.excludeFieldRecursively("*"));
            updateRequest.updates(Update.set("value", "updated"));
            client.data(updateRequest);

            found = find(client, uuid);

            assertNotNull(found);
            assertEquals(hostname, found.getHostname());
            assertEquals("updated", found.getValue());

            DataDeleteRequest deleteRequest = new DataDeleteRequest(ENTITY);
            deleteRequest.where(
                    Query.or(
                            Query.withValue("_id", Query.eq, uuid),
                            Query.withValue("creationDate", Query.lte,
                                    Date.from(LocalDateTime
                                            .now()
                                            .minusMinutes(5)
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()))
                    )
            );
            client.data(deleteRequest);

            assertNull(find(client, uuid));

            LOGGER.debug("Health check passed.");

            return new HealthCheck().success();
        } catch (Throwable e) {
            LOGGER.error("Health check failed.", e);
            String errorMessage = " client=" + clientName + " error=" + e.getMessage() + " ";
            return new HealthCheck().error(errorMessage);
        }
    }

    private Test find(LightblueClient client, String uuid) throws LightblueException {
        DataFindRequest findRequest = new DataFindRequest(ENTITY);
        findRequest.select(Projection.includeFieldRecursively("*"));
        findRequest.where(Query.withValue("_id", Query.eq, uuid));
        findRequest.execution(MongoExecution.withReadPreference(ReadPreference.primary));
        return client.data(findRequest, Test.class);
    }

}
