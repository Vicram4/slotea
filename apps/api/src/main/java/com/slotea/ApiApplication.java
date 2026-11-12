package com.slotea;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

    /** Reloj inyectable: toda la lógica que dependa de la hora se puede probar con un reloj fijo. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
