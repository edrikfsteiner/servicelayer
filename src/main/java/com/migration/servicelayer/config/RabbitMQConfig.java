package com.migration.servicelayer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final String EXCHANGE_NAME = "migration.exchange";
    private static final String MAIN_QUEUE = "migration.data.queue";
    private static final String DLQ_QUEUE = "migration.data.dlq";
    private static final String ROUTING_MAIN_KEY = "migration.routing.key";
    private static final String ROUTING_KEY_DLQ = "migration.dlq.routing.key";

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Queue mainQueue() {
        return QueueBuilder.durable(MAIN_QUEUE)
                .withArgument("x-dead-letter-exchange", EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding bindingMainQueue(Queue mainQueue, DirectExchange exchange) {
        return BindingBuilder.bind(mainQueue).to(exchange).with(ROUTING_MAIN_KEY);
    }

    @Bean
    public Binding bindingDLQ(Queue dlq, DirectExchange exchange) {
        return BindingBuilder.bind(dlq).to(exchange).with(ROUTING_KEY_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}