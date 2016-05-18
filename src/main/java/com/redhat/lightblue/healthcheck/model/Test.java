package com.redhat.lightblue.healthcheck.model;

import java.util.Date;

public class Test {

    private String _id;
    private String hostname;
    private String value;
    private Date creationDate;

    public Test() {
    }

    public Test(String hostname, String value) {
        this.hostname = hostname;
        this.value = value;
    }

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

}
