package com.xelastay.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void declaresDurableTopicExchange() {
        TopicExchange exchange = config.eventsExchange();

        assertThat(exchange.getName()).isEqualTo("xela.events");
        assertThat(exchange.getType()).isEqualTo(ExchangeTypes.TOPIC);
        assertThat(exchange.isDurable()).isTrue();
    }

    @Test
    void bookingNotificationsQueueSendsFailuresToItsDlq() {
        Queue queue = config.bookingNotificationsQueue();

        assertThat(queue.getName()).isEqualTo("booking.notifications");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "xela.events.dlx")
                .containsEntry("x-dead-letter-routing-key", "booking.notifications.dlq");
    }

    @Test
    void bindsAllBookingEventsToTheNotificationsQueue() {
        Binding binding = config.bookingNotificationsBinding();
        Binding deadLetter = config.bookingNotificationsDeadLetterBinding();

        assertThat(binding.getExchange()).isEqualTo("xela.events");
        assertThat(binding.getRoutingKey()).isEqualTo("booking.#");
        assertThat(binding.getDestination()).isEqualTo("booking.notifications");
        assertThat(deadLetter.getExchange()).isEqualTo("xela.events.dlx");
        assertThat(deadLetter.getDestination()).isEqualTo("booking.notifications.dlq");
    }

    @Test
    void envelopeSurvivesTheJsonRoundTrip() {
        MessageConverter converter = config.jsonMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        UUID bookingId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "booking:" + bookingId + ":CONFIRMED",
                EventTypes.BOOKING_CONFIRMED, EventTypes.BOOKING_AGGREGATE, bookingId, Instant.now(),
                Map.of("bookingId", bookingId.toString(), "checkIn", "2026-11-04"));

        Message message = converter.toMessage(envelope, new MessageProperties());
        message.getMessageProperties().setInferredArgumentType(EventEnvelope.class);

        assertThat(converter.fromMessage(message)).isEqualTo(envelope);
    }
}
