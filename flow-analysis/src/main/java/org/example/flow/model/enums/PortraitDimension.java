package org.example.flow.model.enums;

public enum PortraitDimension {

    GENDER("gender"),
    AGE_GROUP("age_group"),
    IS_RESIDENT("is_resident");

    private final String value;

    PortraitDimension(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
