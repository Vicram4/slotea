package com.slotea;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Arranca la aplicación entera contra PostgreSQL real: contexto, Flyway y health. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiApplicationTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void flywayEnablesBtreeGist() {
        var installed = jdbc.sql("SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'")
                .query(Long.class)
                .single();
        assertThat(installed).isEqualTo(1L);
    }

    @Test
    void healthEndpointIsUp() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void openApiDocumentIsPublished() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/ping']").isNotNull();
    }
}
