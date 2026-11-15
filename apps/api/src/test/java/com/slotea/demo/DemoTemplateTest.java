package com.slotea.demo;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Una plantilla mal escrita falla al cargar la clase, no al crear una copia en producción. */
class DemoTemplateTest {

    @ParameterizedTest
    @ValueSource(strings = { "#ffd700", "#1E3A8A", "navy" })
    void rejectsBrandColorsTheApiWouldNotStore(String color) {
        assertThatIllegalArgumentException().isThrownBy(() -> withColor(color))
                .withMessageContaining("brand color");
    }

    @ParameterizedTest
    @ValueSource(strings = { "#1e3a8a", "#18181b" })
    void acceptsReadableLowercaseColors(String color) {
        assertThatNoException().isThrownBy(() -> withColor(color));
    }

    private static DemoTemplate withColor(String color) {
        DemoTemplate t = DemoTemplates.BARBERSHOP;
        return new DemoTemplate(t.businessName(), t.contactEmail(), color, t.services(), t.staff(),
                t.customerFirstNames(), t.notes(), t.slotStepMin());
    }
}
