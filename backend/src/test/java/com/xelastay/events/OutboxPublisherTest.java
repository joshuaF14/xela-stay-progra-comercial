package com.xelastay.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 10;

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private EventSender sender;

    @Mock
    private PlatformTransactionManager transactionManager;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, sender, new EventsProperties(BATCH_SIZE, MAX_ATTEMPTS),
                transactionManager);
    }

    @Test
    void marksSentEventsAsPublishedAndFailuresAsFailed() {
        OutboxEvent sent = event();
        OutboxEvent rejected = event();
        when(repository.lockNextBatch(MAX_ATTEMPTS, BATCH_SIZE)).thenReturn(List.of(sent, rejected));
        doNothing().when(sender).send(sent);
        doThrow(new AmqpException("nack del broker")).when(sender).send(rejected);

        publisher.publishPending();

        assertThat(sent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(sent.getPublishedAt()).isNotNull();
        assertThat(sent.getAttempts()).isZero();
        assertThat(rejected.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(rejected.getAttempts()).isEqualTo(1);
        assertThat(rejected.getPublishedAt()).isNull();
        verify(transactionManager).commit(any());
    }

    @Test
    void stopsTheBatchWithoutSpendingAttemptsWhenBrokerIsDown() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(repository.lockNextBatch(MAX_ATTEMPTS, BATCH_SIZE)).thenReturn(List.of(first, second));
        doThrow(new AmqpConnectException(new ConnectException("Connection refused"))).when(sender).send(first);

        publisher.publishPending();

        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(first.getAttempts()).isZero();
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(sender, never()).send(second);
    }

    @Test
    void retriedFailedEventCanBePublished() {
        OutboxEvent event = event();
        event.markFailed();
        when(repository.lockNextBatch(MAX_ATTEMPTS, BATCH_SIZE)).thenReturn(List.of(event));

        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    private static OutboxEvent event() {
        UUID bookingId = UUID.randomUUID();
        return OutboxEvent.pending(OutboxService.eventKey(EventTypes.BOOKING_AGGREGATE, bookingId, "CREATED"),
                EventTypes.BOOKING_AGGREGATE, bookingId, EventTypes.BOOKING_CREATED, Map.of("bookingId", bookingId));
    }
}
