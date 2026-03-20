package com.migration.servicelayer.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class MigrationService {

    private final JdbcTemplate originJdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    public MigrationService(JdbcTemplate originJdbcTemplate, RabbitTemplate rabbitTemplate) {
        this.originJdbcTemplate = originJdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void startMassMigration(String originTable, String targetTable) {
        String sql = "SELECT * FROM " + originTable;
        List<Map<String, Object>> originData = originJdbcTemplate.queryForList(sql);

        originData.forEach(row -> {
            rabbitTemplate.convertAndSend("migration.exchange", "migration.routing.key", row, message -> {
                message.getMessageProperties().setHeader("targetTable", targetTable);
                return message;
            });
            // TODO: não usar sout
            System.out.println("Enviado para a fila registro da tabela: " + originTable);
        });
    }
}