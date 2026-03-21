package com.migration.servicelayer.service;

import com.migration.servicelayer.util.TableNameValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MigrationService {

    private final JdbcTemplate originJdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ProtocolService protocolService;

    public MigrationService(
            @Qualifier("originJdbcTemplate") JdbcTemplate originJdbcTemplate,
            RabbitTemplate rabbitTemplate,
            ProtocolService protocolService
    ) {
        this.originJdbcTemplate = originJdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.protocolService = protocolService;
    }

    public String startMassMigration(String originTable, String targetTable) {
        TableNameValidator.validate(originTable);
        TableNameValidator.validate(targetTable);

        List<Map<String, Object>> originData = originJdbcTemplate.queryForList("SELECT * FROM " + originTable);

        String protocolId = protocolService.createProtocol(originTable, targetTable, originData.size());

        originData.forEach(row -> rabbitTemplate.convertAndSend(
                "migration.exchange", "migration.routing.key", row, message -> {
                    message.getMessageProperties().setHeader("targetTable", targetTable);
                    message.getMessageProperties().setHeader("protocolId", protocolId);
                    return message;
                }
        ));

        log.info(
                "Protocolo {}: {} registros enfileirados de '{}' para '{}'",
                protocolId, originData.size(), originTable, targetTable
        );

        return protocolId;
    }
}