package com.migration.servicelayer.dto;

/**
 * Represents a single field transformation rule inferred by the AI Profiler
 * and stored in the {@code silver_rules} collection.
 *
 * @param fieldName  exact field name as it appears in the bronze payload
 * @param targetType target type after coercion: {@code STRING | INTEGER | DOUBLE | DATE}
 * @param trim       whether to apply {@link String#trim()} before type conversion
 * @param nullable   whether a null or missing value is acceptable for this field
 */
public record SilverFieldRule(
        String fieldName,
        String targetType,
        boolean trim,
        boolean nullable
) {}
