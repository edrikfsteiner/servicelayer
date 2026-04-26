package com.migration.servicelayer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.queue-main}")
    private String mainQueue;

    @Value("${app.messaging.queue-dlq}")
    private String dlqQueue;

    @Value("${app.messaging.queue-transformation}")
    private String transformationQueue;

    @Value("${app.messaging.routing-key-main}")
    private String mainRoutingKey;

    @Value("${app.messaging.routing-key-dlq}")
    private String dlqRoutingKey;

    @Value("${app.messaging.routing-key-transformation}")
    private String transformationRoutingKey;

    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(dlqQueue).build();
    }

    @Bean
    public Queue mainQueue() {
        return QueueBuilder.durable(mainQueue)
                .withArgument("x-dead-letter-exchange", exchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue transformationQueue() {
        return QueueBuilder.durable(transformationQueue)
                .withArgument("x-dead-letter-exchange", exchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Binding bindingMainQueue(Queue mainQueue, DirectExchange exchange) {
        return BindingBuilder.bind(mainQueue).to(exchange).with(mainRoutingKey);
    }

    @Bean
    public Binding bindingDLQ(Queue dlq, DirectExchange exchange) {
        return BindingBuilder.bind(dlq).to(exchange).with(dlqRoutingKey);
    }

    @Bean
    public Binding bindingTransformationQueue(Queue transformationQueue, DirectExchange exchange) {
        return BindingBuilder.bind(transformationQueue).to(exchange).with(transformationRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}