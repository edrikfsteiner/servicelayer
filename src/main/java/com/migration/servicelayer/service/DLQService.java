package com.migration.servicelayer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DLQService {

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.queue-dlq}")
    private String dlqQueue;

    @Value("${app.messaging.routing-key-main}")
    private String mainRoutingKey;

    private final RabbitTemplate rabbitTemplate;

    public DLQService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public long count() {
        Long messageCount = rabbitTemplate.execute(channel -> {
            var result = channel.queueDeclarePassive(dlqQueue);
            return (long) result.getMessageCount();
        });
        return messageCount != null ? messageCount : 0;
    }

    public int reprocessAll() {
        int count = 0;
        Message message;
        while ((message = rabbitTemplate.receive(dlqQueue)) != null) {
            message.getMessageProperties().setHeader("reprocessed", true);
            rabbitTemplate.send(exchange, mainRoutingKey, message);
            count++;
        }
        log.info("Reprocessados {} registros da DLQ", count);
        return count;
    }
}
