package com.slotea.business;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.slotea.shared.validation.BrandColor;
import com.slotea.shared.validation.TimeZoneId;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BusinessDtos {

    private BusinessDtos() {
    }

    public record BusinessResponse(UUID id, String slug, String name, String timezone, String contactEmail,
            @Schema(example = "#1e3a8a") String brandColor, boolean demo, Instant createdAt) {

        static BusinessResponse from(Business b) {
            return new BusinessResponse(b.getId(), b.getSlug(), b.getName(), b.getTimezone(), b.getContactEmail(),
                    b.getBrandColor(), b.isDemo(), b.getCreatedAt());
        }
    }

    /** Cambios parciales: los campos que no vienen (o son {@code null}) no se tocan. */
    public record UpdateBusinessRequest(
            @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "no puede estar vacío")
            @Nullable String name,
            @TimeZoneId @Nullable String timezone,
            @Email @Size(max = 254) @Nullable String contactEmail,
            @BrandColor @Schema(example = "#1e3a8a") @Nullable String brandColor) {
    }

    public record MemberResponse(UUID userId, String email, String fullName, Role role, Instant joinedAt) {

        static MemberResponse from(Membership m) {
            return new MemberResponse(m.getUser().getId(), m.getUser().getEmail(), m.getUser().getFullName(),
                    m.getRole(), m.getCreatedAt());
        }
    }
}
