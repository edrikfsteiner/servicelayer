package com.migration.servicelayer.controller;

import com.migration.servicelayer.dto.IngestionResponse;
import com.migration.servicelayer.dto.ProtocolResponse;
import com.migration.servicelayer.model.ProtocolStatus;
import com.migration.servicelayer.service.IngestionService;
import com.migration.servicelayer.service.ProtocolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService service;
    private final ProtocolService protocolService;

    @PostMapping
    public ResponseEntity<IngestionResponse> ingest(
            @RequestHeader(value = "X-Event-Type", defaultValue = "raw_data") String eventType,
            @RequestBody Map<String, Object> payload,
            JwtAuthenticationToken token
    ) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        return ResponseEntity.accepted().body(new IngestionResponse(
                service.publishToQueue(tenantId, eventType, payload),
                ProtocolStatus.QUEUED.toString(),
                "Dado recebido e enfileirado para processamento."
        ));
    }

    @PostMapping("/batch")
    public ResponseEntity<IngestionResponse> ingestBatch(
            @RequestHeader(value = "X-Event-Type", defaultValue = "raw_data") String eventType,
            @RequestBody List<Map<String, Object>> payloads,
            JwtAuthenticationToken token
    ) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        return ResponseEntity.accepted().body(new IngestionResponse(
                service.publishBatchToQueue(tenantId, eventType, payloads),
                ProtocolStatus.QUEUED.toString(),
                "Lote recebido e enfileirado para processamento."
        ));
    }

    @GetMapping("/status/{protocolId}")
    public ResponseEntity<ProtocolResponse> status(@PathVariable String protocolId, JwtAuthenticationToken token) {
        String tenantId = (String) token.getTokenAttributes().get("tenantId");
        return ResponseEntity.ok(protocolService.getStatus(protocolId, tenantId));
    }
}
