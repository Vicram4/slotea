package com.slotea.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeZoneIdTest {

    private final TimeZoneId.Validator validator = new TimeZoneId.Validator();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "Europe/Madrid", "Atlantic/Canary", "America/Argentina/Buenos_Aires" })
    void acceptsRegionIds(String zone) {
        assertThat(validator.isValid(zone, null)).isTrue();
    }

    /** Los offsets fijos no tienen cambio de hora: los huecos saldrían mal medio año. */
    @ParameterizedTest
    @ValueSource(strings = { "", "UTC", "+02:00", "GMT+1", "Mars/Base", "europe/madrid" })
    void rejectsFixedOffsetsAndUnknownZones(String zone) {
        assertThat(validator.isValid(zone, null)).isFalse();
    }
}
