package com.migration.servicelayer.service;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private DlqService dlqService;

    @Test
    void count_shouldReturnMessageCount() {
        when(rabbitTemplate.execute(any())).thenAnswer(invocation -> {
            var callback = invocation.getArgument(0, org.springframework.amqp.rabbit.core.ChannelCallback.class);
            Channel channel = mock(Channel.class);
            AMQP.Queue.DeclareOk declareOk = mock(AMQP.Queue.DeclareOk.class);
            when(declareOk.getMessageCount()).thenReturn(5);
            when(channel.queueDeclarePassive("migration.data.dlq")).thenReturn(declareOk);
            return callback.doInRabbit(channel);
        });

        assertEquals(5, dlqService.count());
    }

    @Test
    void count_shouldReturnZeroWhenNull() {
        when(rabbitTemplate.execute(any())).thenReturn(null);

        assertEquals(0, dlqService.count());
    }

    @Test
    void reprocessAll_shouldMoveAllMessagesToMainQueue() {
        Message msg1 = new Message("data1".getBytes(), new MessageProperties());
        Message msg2 = new Message("data2".getBytes(), new MessageProperties());

        when(rabbitTemplate.receive("migration.data.dlq"))
                .thenReturn(msg1)
                .thenReturn(msg2)
                .thenReturn(null);

        int count = dlqService.reprocessAll();

        assertEquals(2, count);
        verify(rabbitTemplate, times(2)).send(eq("migration.exchange"), eq("migration.routing.key"), any(Message.class));
        assertTrue(msg1.getMessageProperties().getHeaders().containsKey("reprocessed"));
    }

    @Test
    void reprocessAll_shouldReturnZeroWhenEmpty() {
        when(rabbitTemplate.receive("migration.data.dlq")).thenReturn(null);

        assertEquals(0, dlqService.reprocessAll());
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }
}
