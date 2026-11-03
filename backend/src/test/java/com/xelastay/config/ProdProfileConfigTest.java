package com.xelastay.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;

/** Revisa el perfil prod sin levantar el contexto (no hay base de datos ni RabbitMQ en el CI). */
class ProdProfileConfigTest {

    @Test
    void prodProfileKeepsFlywayOffAndHidesHealthDetails() throws IOException {
        Properties prod = load("application-prod.properties");

        assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(prod.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
        assertThat(prod.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(prod.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        assertThat(prod.getProperty("spring.rabbitmq.addresses")).isEqualTo("${RABBITMQ_URL:}");
    }

    @Test
    void cloudAmqpUrlEnablesTlsAndCarriesCredentialsAndVhost() {
        RabbitProperties rabbit = new RabbitProperties();
        rabbit.setAddresses(List.of("amqps://usuario:clave@puffin.rmq2.cloudamqp.com/vhost1"));

        assertThat(rabbit.getSsl().determineEnabled()).isTrue();
        assertThat(rabbit.determinePort()).isEqualTo(5671);
        assertThat(rabbit.determineHost()).isEqualTo("puffin.rmq2.cloudamqp.com");
        assertThat(rabbit.determineUsername()).isEqualTo("usuario");
        assertThat(rabbit.determineVirtualHost()).isEqualTo("vhost1");
    }

    @Test
    void emptyRabbitUrlFallsBackToHostAndPort() {
        RabbitProperties rabbit = new RabbitProperties();
        rabbit.setAddresses(List.of());

        assertThat(rabbit.determineHost()).isEqualTo("localhost");
        assertThat(rabbit.getSsl().determineEnabled()).isFalse();
    }

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ProdProfileConfigTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
