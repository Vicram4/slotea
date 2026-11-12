package com.slotea;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Arranca la aplicación con el perfil de producción: un error en application-prod.yaml
 * tiene que romper la CI, no el despliegue en Cloud Run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ProdProfileTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    HikariDataSource dataSource;

    @Test
    void connectionPoolFitsNeonFreeLimits() {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
        assertThat(dataSource.getMinimumIdle()).isZero();
    }

    @Test
    void healthIsUpWithoutLeakingDetails() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .hasPath("$.status")
                .doesNotHavePath("$.components");
    }
}
