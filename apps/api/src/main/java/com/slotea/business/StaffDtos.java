package com.slotea.business;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StaffDtos {

    private StaffDtos() {
    }

    /** {@code serviceIds}: los servicios que hace (ordenados por nombre). */
    public record StaffResponse(UUID id, String displayName, @Nullable UUID userId, boolean active,
            List<UUID> serviceIds, Instant createdAt) {

        static StaffResponse from(StaffMember s, List<UUID> serviceIds) {
            return new StaffResponse(s.getId(), s.getDisplayName(), s.getUserId(), s.isActive(), serviceIds,
                    s.getCreatedAt());
        }
    }

    /**
     * Alta y edición (PUT: el profesional completo). {@code userId}: un miembro del negocio, para
     * enlazar la ficha con su usuario; {@code null} = sin usuario. {@code active} omitido = activo.
     */
    public record StaffRequest(
            @NotNull @Size(min = 1, max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "no puede estar vacío")
            String displayName,
            @Nullable UUID userId,
            @Nullable Boolean active) {
    }

    public record StaffServicesRequest(@NotNull @Size(max = 200) List<@NotNull UUID> serviceIds) {
    }
}
