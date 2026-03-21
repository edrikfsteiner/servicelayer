package com.migration.servicelayer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DlqService {

    private static final String DLQ_QUEUE = "migration.data.dlq";
    private static final String EXCHANGE = "migration.exchange";
    private static final String ROUTING_KEY = "migration.routing.key";

    private final RabbitTemplate rabbitTemplate;

    public DlqService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public long count() {
        Long messageCount = rabbitTemplate.execute(channel -> {
            var result = channel.queueDeclarePassive(DLQ_QUEUE);
            return (long) result.getMessageCount();
        });
        return messageCount != null ? messageCount : 0;
    }

    public int reprocessAll() {
        int count = 0;
        Message message;
        while ((message = rabbitTemplate.receive(DLQ_QUEUE)) != null) {
            message.getMessageProperties().setHeader("reprocessed", true);
            rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message);
            count++;
        }
        log.info("Reprocessados {} registros da DLQ", count);
        return count;
    }
}
