package com.redhat.lightblue.healthcheck;

import static com.redhat.lightblue.util.test.AbstractJsonNodeTest.loadJsonNode;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;

import org.jboss.resteasy.plugins.server.sun.http.HttpContextBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.lightblue.client.integration.test.LightblueExternalResource;
import com.redhat.lightblue.client.integration.test.LightblueExternalResource.LightblueTestMethods;
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

    @Test
    public void testCheck() {
        assertTrue(true);
    }

}
