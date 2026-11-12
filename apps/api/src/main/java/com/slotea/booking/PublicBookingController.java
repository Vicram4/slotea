package com.slotea.booking;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.booking.PublicDtos.AvailabilityResponse;
import com.slotea.booking.PublicDtos.BookingRequest;
import com.slotea.booking.PublicDtos.BookingResponse;
import com.slotea.booking.PublicDtos.PublicBusinessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Página pública de reservas, sin sesión. El {@code slug} no se usa aquí: {@link PublicTenantFilter}
 * ya ha fijado ese negocio como tenant (o ha respondido 404). Se declara para documentar la ruta.
 */
@Tag(name = "public")
@RestController
@RequestMapping("/api/v1/public/businesses/{slug}")
class PublicBookingController {

    private final PublicBusinessService businesses;
    private final AvailabilityService availability;
    private final BookingService bookings;

    PublicBookingController(PublicBusinessService businesses, AvailabilityService availability,
            BookingService bookings) {
        this.businesses = businesses;
        this.availability = availability;
        this.bookings = bookings;
    }

    @Operation(summary = "Datos públicos del negocio: servicios y profesionales que se pueden reservar")
    @GetMapping
    PublicBusinessResponse business(@PathVariable String slug) {
        return businesses.current();
    }

    @Operation(summary = "Huecos libres de un servicio por día, en la zona horaria del negocio (máx. 14 días)")
    @GetMapping("/availability")
    AvailabilityResponse availability(@PathVariable String slug, @RequestParam UUID serviceId,
            @RequestParam @Nullable UUID staffId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return availability.availability(serviceId, staffId, from, to);
    }

    @Operation(summary = "Reservar como invitado. Sin staffId, la API asigna un profesional libre. 409 si el hueco ya no está libre")
    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    BookingResponse book(@PathVariable String slug, @Valid @RequestBody BookingRequest request) {
        return bookings.book(request);
    }
}
