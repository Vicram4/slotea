package com.slotea;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.slotea.support.Mailpit;
import com.slotea.support.MailpitContainer;

/**
 * Servicios reales para los tests de integración: PostgreSQL (la misma versión que en producción)
 * y Mailpit como servidor SMTP (los emails se envían de verdad y se comprueban por su API).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    MailpitContainer mailpitContainer() {
        return new MailpitContainer();
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(MailpitContainer mailpit) {
        return registry -> {
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", mailpit::smtpPort);
        };
    }

    @Bean
    Mailpit mailpit(MailpitContainer container) {
        return new Mailpit(container);
    }
}
