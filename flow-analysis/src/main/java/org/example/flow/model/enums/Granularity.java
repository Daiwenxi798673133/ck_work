package org.example.flow.model.enums;

public enum Granularity {

    HOUR("hour"),
    DAY("day");

    private final String value;

    Granularity(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
