package com.migration.servicelayer.util;

import java.util.regex.Pattern;

public final class TableNameValidator {

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,127}$");

    private TableNameValidator() {}

    public static void validate(String identifier) {
        if (identifier == null || !VALID_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Identificador SQL inválido: " + identifier);
        }
    }
}
