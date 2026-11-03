package com.xelastay.events;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Topología de RabbitMQ. Spring declara exchanges, colas y bindings al conectarse (si ya existen, no cambia nada).
 * <pre>
 * xela.events (topic) --booking.#--> booking.notifications --(falla 3 veces)--> xela.events.dlx --> booking.notifications.dlq
 * </pre>
 * Se desactiva con xelastay.events.enabled=false (perfil test o sin Docker).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(EventsProperties.class)
@ConditionalOnProperty(prefix = "xelastay.events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitConfig {

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EventTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue bookingNotificationsQueue() {
        return QueueBuilder.durable(EventTopology.BOOKING_NOTIFICATIONS_QUEUE)
                .deadLetterExchange(EventTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EventTopology.BOOKING_NOTIFICATIONS_DLQ)
                .build();
    }

    @Bean
    public Queue bookingNotificationsDeadLetterQueue() {
        return QueueBuilder.durable(EventTopology.BOOKING_NOTIFICATIONS_DLQ).build();
    }

    @Bean
    public Binding bookingNotificationsBinding() {
        return BindingBuilder.bind(bookingNotificationsQueue())
                .to(eventsExchange())
                .with(EventTopology.BOOKING_EVENTS_PATTERN);
    }

    @Bean
    public Binding bookingNotificationsDeadLetterBinding() {
        return BindingBuilder.bind(bookingNotificationsDeadLetterQueue())
                .to(deadLetterExchange())
                .with(EventTopology.BOOKING_NOTIFICATIONS_DLQ);
    }

    /** JSON con el ObjectMapper de Spring (fechas ISO-8601). Lo usan el RabbitTemplate y los @RabbitListener. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
