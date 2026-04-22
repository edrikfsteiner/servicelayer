package com.migration.servicelayer.controller;

import com.migration.servicelayer.service.SchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private final SchemaService service;

    @PostMapping("/{eventType}")
    public ResponseEntity<?> registerSchema(
            @PathVariable String eventType,
            @RequestBody Map<String, Object> fields,
            JwtAuthenticationToken token
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.registerSchema(eventType, fields, token));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @PutMapping("/{eventType}")
    public ResponseEntity<?> updateSchema(
            @PathVariable String eventType,
            @RequestBody Map<String, Object> fields,
            JwtAuthenticationToken token
    ) {
        try {
            return ResponseEntity.ok(service.updateSchema(eventType, fields, token));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @GetMapping
    public ResponseEntity<?> listSchemas(JwtAuthenticationToken token) {
        try {
            return ResponseEntity.ok(service.listSchemas(token));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @GetMapping("/{eventType}")
    public ResponseEntity<?> getSchema(@PathVariable String eventType, JwtAuthenticationToken token) {
        try {
            return ResponseEntity.ok(service.getSchema(eventType, token));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @DeleteMapping("/{eventType}")
    public ResponseEntity<?> deleteSchema(@PathVariable String eventType, JwtAuthenticationToken token) {
        try {
            service.deleteSchema(eventType, token);
            return ResponseEntity.ok("Esquema '" + eventType + "' removido com sucesso.");
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }
}