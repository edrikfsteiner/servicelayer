package com.migration.servicelayer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SchemaProperties {
    PROPERTIES("properties"),
    DEFAULT_VALUE("default-value"),
    RENAME_TO("x-rename-to"),
    TRANSFORM("x-transform"),
    PRIMARY_KEY("x-primary-key");

    private final String value;
}
