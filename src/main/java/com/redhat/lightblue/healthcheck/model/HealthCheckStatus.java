package com.redhat.lightblue.healthcheck.model;

public class HealthCheckStatus {

    public enum Status {
        success("{\"status\":\"success\"}"),
        error("{\"status\":\"error\",\"message\":\"[healthcheckstatus.message]\"}");

        private String message;

        Status(String message) {
            this.message = message;
        }
    }

    Status status;
    String message;

    public HealthCheckStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public HealthCheckStatus withErrorMessage(String errorMessage) {
        this.message = Status.error.message.replace("[healthcheckstatus.message]",errorMessage);
        return this;
    }
}
