package com.slotea;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * El contrato entre la API y la web es el OpenAPI que genera springdoc. Guardamos una copia en
 * {@code apps/web/openapi.json}, de la que la web genera sus tipos de TypeScript.
 *
 * <p>Si cambias la API, este test falla hasta que actualices la copia:
 * {@code ./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true}
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractTests {

    private static final Path SNAPSHOT = Path.of("..", "web", "openapi.json");

    @Autowired
    MockMvcTester mvc;

    @Test
    void openApiSnapshotIsUpToDate() throws IOException {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatus(HttpStatus.OK);

        JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        ObjectNode current = (ObjectNode) json.readTree(result.getResponse().getContentAsString());
        current.remove("servers"); // depende del puerto: no forma parte del contrato

        if (Boolean.getBoolean("openapi.update") || !Files.exists(SNAPSHOT)) {
            Files.writeString(SNAPSHOT, json.writeValueAsString(current) + "\n");
        }
        JsonNode snapshot = json.readTree(Files.readString(SNAPSHOT));
        assertThat(current)
                .as("apps/web/openapi.json está desactualizado: ejecuta "
                        + "./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true y luego pnpm api:types")
                .isEqualTo(snapshot);
    }
}
