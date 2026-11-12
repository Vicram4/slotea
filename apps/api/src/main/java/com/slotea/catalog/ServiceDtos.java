package com.slotea.catalog;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.slotea.shared.validation.MultipleOf;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ServiceDtos {

    private ServiceDtos() {
    }

    public record ServiceResponse(UUID id, String name, @Nullable String description, int durationMin,
            int bufferMin, @Nullable Integer priceCents, boolean active, Instant createdAt) {

        static ServiceResponse from(Offering o) {
            return new ServiceResponse(o.getId(), o.getName(), o.getDescription(), o.getDurationMin(),
                    o.getBufferMin(), o.getPriceCents(), o.isActive(), o.getCreatedAt());
        }
    }

    /**
     * Alta y edición. Es un PUT (se manda el servicio completo): así {@code null} significa «sin
     * precio» o «sin descripción», y no «no lo toques». {@code active} omitido = activo.
     */
    public record ServiceRequest(
            @NotNull @Size(min = 1, max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "no puede estar vacío")
            String name,
            @Size(max = 500) @Nullable String description,
            @NotNull @Min(5) @Max(480) @MultipleOf(5) Integer durationMin,
            @Min(0) @Max(120) @MultipleOf(5) @Nullable Integer bufferMin,
            @Min(0) @Max(10_000_000) @Nullable Integer priceCents,
            @Nullable Boolean active) {
    }
}
