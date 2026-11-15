package com.slotea.notification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import com.slotea.shared.links.AppointmentLinks;

/**
 * Convierte un {@link AppointmentEmail} en asunto + texto plano + HTML (plantillas Thymeleaf en
 * {@code templates/email/}). Las fechas, en español y en la zona horaria del negocio.
 */
@Component
class EmailRenderer {

    static final Locale SPANISH = Locale.of("es", "ES");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", SPANISH);

    private final ITemplateEngine templates;
    private final AppointmentLinks links;

    EmailRenderer(ITemplateEngine templates, AppointmentLinks links) {
        this.templates = templates;
        this.links = links;
    }

    record RenderedEmail(String subject, String text, String html) {
    }

    RenderedEmail render(EmailType type, AppointmentEmail email) {
        ZoneId zone = ZoneId.of(email.timezone());
        var context = new Context(SPANISH);
        context.setVariable("type", type.name());
        context.setVariable("businessName", email.businessName());
        context.setVariable("customerName", email.customerName());
        context.setVariable("serviceName", email.serviceName());
        context.setVariable("staffName", email.staffName());
        context.setVariable("date", format(LONG_DATE, email.startsAt(), zone));
        context.setVariable("time", format(TIME, email.startsAt(), zone));
        context.setVariable("endTime", format(TIME, email.endsAt(), zone));
        if (email.previousStartsAt() != null) {
            context.setVariable("previousDate", format(LONG_DATE, email.previousStartsAt(), zone));
            context.setVariable("previousTime", format(TIME, email.previousStartsAt(), zone));
        }
        String when = format(SHORT_DATE, email.startsAt(), zone) + " a las " + format(TIME, email.startsAt(), zone);
        String subject = switch (type) {
            case BOOKING_CONFIRMED -> "Cita confirmada: " + email.serviceName() + ", " + when;
            case BOOKING_RESCHEDULED -> "Cita cambiada: " + email.serviceName() + ", ahora el " + when;
            case BOOKING_CANCELLED -> "Cita cancelada: " + email.serviceName() + ", " + when;
            case BUSINESS_NEW_BOOKING -> "Nueva reserva: " + email.customerName() + ", " + when;
            case BUSINESS_BOOKING_RESCHEDULED -> "Cita cambiada: " + email.customerName() + ", ahora el " + when;
            case BUSINESS_BOOKING_CANCELLED -> "Cita cancelada: " + email.customerName() + ", " + when;
        };

        String template;
        if (type.toBusiness()) {
            // El negocio no recibe el enlace del cliente (es su acceso a la cita): va a su agenda.
            context.setVariable("customerEmail", email.customerEmail());
            context.setVariable("customerPhone", email.customerPhone());
            context.setVariable("agendaUrl", links.agendaUrl(email.startsAt().atZone(zone).toLocalDate()).toString());
            template = "email/business-notice";
        } else {
            context.setVariable("manageUrl", links.manageUrl(email.businessSlug(), email.appointmentId()).toString());
            context.setVariable("bookingUrl", links.bookingUrl(email.businessSlug()).toString());
            template = "email/appointment";
        }
        return new RenderedEmail(subject, templates.process(template + ".txt", context),
                templates.process(template, context));
    }

    private static String format(DateTimeFormatter formatter, Instant instant, ZoneId zone) {
        return formatter.format(instant.atZone(zone));
    }

    /**
     * El motor de Boot solo resuelve {@code templates/*.html} (modo HTML). Este resolver añade el
     * texto plano ({@code templates/email/*.txt}, modo TEXT): Boot mete en el motor todos los
     * {@link ITemplateResolver} que encuentre.
     */
    @Configuration(proxyBeanMethods = false)
    static class TextTemplates {

        @Bean
        ITemplateResolver emailTextTemplateResolver() {
            var resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/");
            resolver.setResolvablePatterns(Set.of("email/*.txt"));
            resolver.setTemplateMode(TemplateMode.TEXT);
            resolver.setCharacterEncoding("UTF-8");
            resolver.setCheckExistence(true);
            resolver.setOrder(0);
            return resolver;
        }
    }
}
