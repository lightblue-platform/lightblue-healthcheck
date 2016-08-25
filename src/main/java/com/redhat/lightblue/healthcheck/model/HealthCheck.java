package com.redhat.lightblue.healthcheck.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class HealthCheck {

    private boolean hasFailures = false;

    private Map<String, HealthCheckStatus> clientStatuses;

    public boolean hasFailures() {
        return hasFailures;
    }

    public void hasFailures(boolean hasFailures) {
        this.hasFailures = hasFailures;
    }

    public Map<String, HealthCheckStatus> getClientStatuses() {
        return clientStatuses;
    }

    public void setClientStatuses(Map<String, HealthCheckStatus> clientStatuses) {
        this.clientStatuses = clientStatuses;
    }

    private synchronized Map<String, HealthCheckStatus> clientStatuses() {
        if (null == this.clientStatuses) {
            clientStatuses = new LinkedHashMap<>(1);
        }
        return this.clientStatuses;
    }

    public void addStatus(String client, HealthCheckStatus status) {
        clientStatuses().put(client, status);
    }

    public void addStatuses(Map<String, HealthCheckStatus> clientStatues) {
        clientStatuses().putAll(clientStatues);
    }
}