package com.slotea.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.slotea.shared.validation.BrandColor.Colors;

class BrandColorTest {

    private final BrandColor.Validator validator = new BrandColor.Validator();

    /** Los extremos de la escala de WCAG y el gris que se usa de ejemplo de 4,5:1. */
    @Test
    void contrastFollowsWcag() {
        assertThat(Colors.contrastWithWhite("#ffffff")).isEqualTo(1.0, within(0.001));
        assertThat(Colors.contrastWithWhite("#000000")).isEqualTo(21.0, within(0.001));
        assertThat(Colors.contrastWithWhite("#767676")).isEqualTo(4.54, within(0.01));
        assertThat(Colors.contrastWithWhite("#777777")).isEqualTo(4.48, within(0.01));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { BrandColor.DEFAULT, "#000000", "#767676", "#1E3A8A", "#be185d" })
    void acceptsColorsThatReadWellOnWhite(String color) {
        assertThat(validator.isValid(color, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "#ffffff", "#777777", "#ffd700", "#22c55e", "", "red", "#fff", "1e3a8a", "#1e3a8a00" })
    void rejectsLightColorsAndAnythingThatIsNotHex(String color) {
        assertThat(validator.isValid(color, null)).isFalse();
    }

    @Test
    void contrastNeedsAHexColor() {
        assertThatIllegalArgumentException().isThrownBy(() -> Colors.contrastWithWhite("rgb(0,0,0)"));
    }
}
