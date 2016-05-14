package com.redhat.lightblue.healthcheck;

import static com.redhat.lightblue.util.test.AbstractJsonNodeTest.loadJsonNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.plugins.server.sun.http.HttpContextBuilder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.lightblue.client.Projection;
import com.redhat.lightblue.client.Query;
import com.redhat.lightblue.client.http.HttpMethod;
import com.redhat.lightblue.client.integration.test.LightblueExternalResource;
import com.redhat.lightblue.client.integration.test.LightblueExternalResource.LightblueTestMethods;
import com.redhat.lightblue.client.request.data.DataFindRequest;
import com.redhat.lightblue.client.request.data.DataInsertRequest;
import com.redhat.lightblue.rest.integration.LightblueRestTestHarness;
import com.sun.net.httpserver.HttpServer;

public class HealthCheckResourceTest {

    private final static int DEFAULT_PORT = 7000;

    private static HttpServer httpServer;

    @ClassRule
    public static LightblueExternalResource lightblue = new LightblueExternalResource(new LightblueTestMethods() {

        @Override
        public JsonNode[] getMetadataJsonNodes() throws Exception {
            return new JsonNode[]{
                    loadJsonNode("metadata/test.json")
            };
        }
    });

    @BeforeClass
    public static void startHttpServer() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);

        HttpContextBuilder dataContext = new HttpContextBuilder();
        dataContext.getDeployment().getActualResourceClasses().add(HealthCheckResource.class);
        dataContext.setPath("/rest/test");
        dataContext.bind(httpServer);

        httpServer.start();
    }

    @AfterClass
    public static void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        httpServer = null;
    }

    @Before
    public void setup() throws Exception {
        lightblue.ensureHttpServerIsRunning();
    }

    private HttpURLConnection openConnection() throws IOException {
        URL url = new URL("http://localhost:" + DEFAULT_PORT + "/rest/test/health");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(HttpMethod.GET.name());
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Charset", "utf-8");

        return connection;
    }

    private String response(HttpURLConnection connection) throws IOException {
        try (InputStream responseStream = connection.getInputStream()) {
            return readResponseStream(responseStream, connection);
        }
        catch (IOException e) {
            return readResponseStream(connection.getErrorStream(), connection);
        }
        finally{
            connection.disconnect();
        }
    }

    private String readResponseStream(InputStream responseStream, HttpURLConnection connection) throws IOException {
        int contentLength = connection.getContentLength();

        if (contentLength == 0) {
            return "";
        }

        if (contentLength > 0) {
            byte[] responseBytes = new byte[contentLength];
            IOUtils.readFully(responseStream, responseBytes);
            return new String(responseBytes, Charset.forName("UTF-8"));
        }

        return IOUtils.toString(responseStream, Charset.forName("UTF-8"));
    }

    @Test
    public void testHealth_Success() throws Exception {
        String r = response(openConnection());

        assertEquals("{\"status\":\"success\"}", r);

        DataFindRequest find = new DataFindRequest(com.redhat.lightblue.healthcheck.model.Test.ENTITY_NAME);
        find.where(Query.withValue("objectType", Query.eq,
                com.redhat.lightblue.healthcheck.model.Test.ENTITY_NAME));
        find.select(Projection.includeField("_id"));

        com.redhat.lightblue.healthcheck.model.Test[] results = lightblue.getLightblueClient().data(
                find, com.redhat.lightblue.healthcheck.model.Test[].class);

        assertEquals(0, results.length);
    }

    @Test
    public void testHealth_Failure_LbIsDown() throws IOException {
        LightblueRestTestHarness.stopHttpServer();

        String r = response(openConnection());

        assertEquals("{\"status\":\"error\",\"message\":\"java.net.ConnectException: Connection refused\"}", r);
    }

    /**
     * Asserts that any abandoned data is older than a day is deleted.
     */
    @Test
    public void testHealth_CleanupAbandonedData() throws Exception {
        com.redhat.lightblue.healthcheck.model.Test test = new com.redhat.lightblue.healthcheck.model.Test();
        test.setHostname("somehost");
        test.setValue("abandoned record");
        test.setCreationDate(Date.from(LocalDateTime
                                    .now()
                                    .minusDays(2)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()));

        DataInsertRequest insertRequest = new DataInsertRequest(com.redhat.lightblue.healthcheck.model.Test.ENTITY_NAME);
        insertRequest.create(test);
        insertRequest.returns(Projection.includeField("_id"));

        String abandonedUuid = lightblue.getLightblueClient().data(
                insertRequest,
                com.redhat.lightblue.healthcheck.model.Test.class).get_id();
        assertNotNull(abandonedUuid);

        String r = response(openConnection());
        assertEquals("{\"status\":\"success\"}", r);

        DataFindRequest findRequest = new DataFindRequest(com.redhat.lightblue.healthcheck.model.Test.ENTITY_NAME);
        findRequest.where(Query.withValue("_id", Query.eq, abandonedUuid));
        findRequest.select(Projection.includeField("_id"));

        assertNull(lightblue.getLightblueClient().data(
                findRequest,
                com.redhat.lightblue.healthcheck.model.Test.class));
    }

}
