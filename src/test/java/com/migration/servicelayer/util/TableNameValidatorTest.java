package com.migration.servicelayer.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TableNameValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"clientes", "users", "clientes_legado", "_temp", "A123"})
    void shouldAcceptValidIdentifiers(String identifier) {
        assertDoesNotThrow(() -> TableNameValidator.validate(identifier));
    }

    @Test
    void shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> TableNameValidator.validate(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1starts_with_number", "has spaces", "has-dash", "DROP TABLE x;--", "table.name", "tbl; DELETE FROM x"})
    void shouldRejectInvalidIdentifiers(String identifier) {
        assertThrows(IllegalArgumentException.class, () -> TableNameValidator.validate(identifier));
    }

    @Test
    void shouldRejectIdentifierOver128Chars() {
        String longName = "a".repeat(129);
        assertThrows(IllegalArgumentException.class, () -> TableNameValidator.validate(longName));
    }

    @Test
    void shouldAcceptIdentifierExactly128Chars() {
        String name = "a".repeat(128);
        assertDoesNotThrow(() -> TableNameValidator.validate(name));
    }
}
