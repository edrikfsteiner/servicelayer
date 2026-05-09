package com.migration.servicelayer.exception;

import com.networknt.schema.ValidationMessage;
import lombok.Getter;

import java.util.Set;

@Getter
public class SchemaValidationException extends RuntimeException {
    
    private final Set<ValidationMessage> validationErrors;

    public SchemaValidationException(String message, Set<ValidationMessage> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }
}