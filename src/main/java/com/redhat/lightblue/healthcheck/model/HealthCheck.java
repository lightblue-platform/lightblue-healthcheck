package com.redhat.lightblue.healthcheck.model;

public class HealthCheck {

    public static String SUCCESS = "success";
    public static String ERROR = "error";

    private boolean failed = false;
    private String status;
    private String message;

    public String getMessage() {
        return message;
    }


    public HealthCheck withStatus(String status) {
        this.status = status;
        return this;
    }

    public HealthCheck withMessage(String message) {
        this.message = message;
        return this;
    }

    public HealthCheck withFailure(boolean failed) {
        this.failed = failed;
        return this;
    }

    public static HealthCheck success() {
        return new HealthCheck()
                .withStatus(SUCCESS)
                .withFailure(false);
    }

    public static HealthCheck error(String message) {
        return new HealthCheck()
                .withStatus(ERROR)
                .withFailure(true)
                .withMessage(message);
    }

    public boolean failed() {
        return failed;
    }

    public void failed(boolean failed) {
        this.failed = failed;
    }

}