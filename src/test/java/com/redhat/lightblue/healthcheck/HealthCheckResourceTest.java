package com.redhat.lightblue.healthcheck;

import static com.redhat.lightblue.util.test.AbstractJsonNodeTest.loadJsonNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
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
import com.redhat.lightblue.client.integration.test.LightblueExternalResource.LightblueTestHarnessConfig;
import com.redhat.lightblue.client.request.data.DataFindRequest;
import com.redhat.lightblue.client.request.data.DataInsertRequest;
import com.redhat.lightblue.rest.integration.LightblueRestTestHarness;

import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;

public class HealthCheckResourceTest {

    private final static int DEFAULT_PORT = 7000;

    private static UndertowJaxrsServer httpServer;

    @ClassRule
    public static LightblueExternalResource lightblue = new LightblueExternalResource(new LightblueTestHarnessConfig() {

        @Override
        public JsonNode[] getMetadataJsonNodes() throws Exception {
            return new JsonNode[]{
                loadJsonNode("metadata/test.json")
            };
        }
    });

    @BeforeClass
    public static void startHttpServer() throws Exception {
        ResteasyDeployment healthDeployment = new ResteasyDeployment();
        healthDeployment.getActualResourceClasses().add(HealthCheckResource.class);

        Undertow.Builder builder = Undertow.builder()
                .addHttpListener(DEFAULT_PORT, "localhost");

        httpServer = new UndertowJaxrsServer();
        httpServer.start(builder);

        DeploymentInfo healthDeploymentInfo = httpServer.undertowDeployment(healthDeployment);
        healthDeploymentInfo.setClassLoader(HealthCheckResourceTest.class.getClassLoader());
        healthDeploymentInfo.setDeploymentName("health");
        healthDeploymentInfo.setContextPath("/rest/test");

        httpServer.deploy(healthDeploymentInfo);
    }

    @AfterClass
    public static void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
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
        } catch (IOException e) {
            return readResponseStream(connection.getErrorStream(), connection);
        } finally {
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

        assertTrue(r.contains("Connection refused"));
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
