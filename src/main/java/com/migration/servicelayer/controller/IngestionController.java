package com.migration.servicelayer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.service.IngestionService;
import com.migration.servicelayer.service.ProtocolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private final ProtocolService protocolService;

    public IngestionController(IngestionService ingestionService, ProtocolService protocolService) {
        this.ingestionService = ingestionService;
        this.protocolService = protocolService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receiveData(
            @RequestHeader(value = "X-Event-Type", defaultValue = "raw_data") String eventType,
            @RequestBody JsonNode payload,
            JwtAuthenticationToken token
    ) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        return ResponseEntity.accepted().body(Map.of(
                "protocolId", ingestionService.publishToQueue(tenantId, eventType, payload),
                "status", ProtocolStatus.QUEUED.toString(),
                "message", "Dado recebido e enfileirado para processamento."
        ));
    }

    @GetMapping("/status/{protocolId}")
    public ResponseEntity<ProtocolResponse> status(@PathVariable String protocolId, JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        return ResponseEntity.ok(protocolService.getStatus(protocolId, tenantId));
    }
}