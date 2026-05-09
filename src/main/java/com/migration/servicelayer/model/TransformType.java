package com.migration.servicelayer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransformType {
    UPPERCASE("uppercase"),
    LOWERCASE("lowercase"),
    TRIM("trim");

    private final String value;
}
