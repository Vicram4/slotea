package com.slotea.demo;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.slotea.booking.AppointmentStatus;
import com.slotea.shared.links.AppointmentLinks;

/**
 * Crea una copia de la barbería de demostración: negocio, dueño, empleado con cuenta, catálogo,
 * horarios, un cierre, una ausencia, clientes y unas 4 semanas de citas <strong>relativas a hoy</strong>
 * (en la zona del negocio). Todo es ficticio: emails {@code @example.com} (dominio reservado) y sin
 * teléfonos.
 *
 * <p>Con SQL por lotes y no con JPA: son unas 500 filas por clic y tienen que estar en menos de un
 * segundo. Las citas pasan igualmente por la restricción {@code appointment_no_overlap}: si el
 * generador se equivocara, el seed fallaría en vez de crear una agenda imposible.
 *
 * <p>El contenido sale de un {@link Random} con semilla fija: dos copias creadas a la misma hora son
 * iguales (y los tests, reproducibles). Solo el slug es aleatorio.
 */
@Component
class DemoSeeder {

    static final String BUSINESS_NAME = "Barbería Demo Slotea";
    static final String TIMEZONE = "Europe/Madrid";
    static final String CONTACT_EMAIL = "barberia-demo@example.com";
    static final String SLUG_PREFIX = "demo-";
    static final String OWNER_NAME = "Dueño Demo";
    static final String EMPLOYEE_NAME = "Empleado Demo";
    static final String UNLINKED_NAME = "Profesional Demo";
    static final int DAYS_BACK = 14;
    static final int DAYS_AHEAD = 14;
    /** Las citas que empezaron hace menos de esto quedan casi todas sin marcar: para probar la agenda. */
    static final Duration RECENT = Duration.ofDays(3);

    /**
     * Hash que ningún login acepta: los usuarios de la demo solo entran con los botones «Entrar como…».
     * No es un BCrypt válido, así que {@code matches} devuelve siempre {@code false}.
     */
    static final String NO_PASSWORD = "{bcrypt}demo-account-without-password";

    private static final long CONTENT_SEED = 2026L;
    private static final String SLUG_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom SLUGS = new SecureRandom();

    private record ServiceDef(String name, String description, int durationMin, int bufferMin, int priceCents) {
    }

    private static final List<ServiceDef> SERVICES = List.of(
            new ServiceDef("Corte de pelo", "Lavado, corte y peinado.", 30, 0, 1500),
            new ServiceDef("Arreglo de barba", "Perfilado con navaja y toalla caliente.", 20, 0, 1000),
            new ServiceDef("Corte y barba", "El corte de pelo y el arreglo de barba en una sola cita.", 45, 5, 2200),
            new ServiceDef("Corte infantil", "Para menores de 12 años.", 20, 0, 1000));

    /** Quién es cada profesional, qué servicios hace (índices de {@link #SERVICES}) y qué días trabaja. */
    private enum Account { OWNER, EMPLOYEE, NONE }

    private record StaffDef(String name, Account account, List<Integer> services, Set<DayOfWeek> days) {
    }

    private static final Set<DayOfWeek> MON_TO_SAT = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
    private static final List<StaffDef> STAFF = List.of(
            new StaffDef(OWNER_NAME, Account.OWNER, List.of(0, 1, 2, 3), MON_TO_SAT),
            new StaffDef(EMPLOYEE_NAME, Account.EMPLOYEE, List.of(0, 1, 2), MON_TO_SAT),
            new StaffDef(UNLINKED_NAME, Account.NONE, List.of(0, 1, 3), EnumSet.range(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)));

    private record Range(LocalTime start, LocalTime end) {
    }

    /** Jornada partida entre semana; el sábado, solo por la mañana. El domingo cierra. */
    private static List<Range> hours(DayOfWeek day) {
        if (day == DayOfWeek.SATURDAY) {
            return List.of(new Range(LocalTime.of(10, 0), LocalTime.of(14, 0)));
        }
        return List.of(new Range(LocalTime.of(9, 30), LocalTime.of(14, 0)),
                new Range(LocalTime.of(16, 30), LocalTime.of(20, 0)));
    }

    /** Nombres claramente inventados (todos se apellidan «Ejemplo» o «Prueba»). */
    private static final List<String> FIRST_NAMES = List.of(
            "Lucía", "Mario", "Sofía", "Hugo", "Martina", "Pablo", "Julia", "Daniel", "Paula", "Álvaro",
            "Carmen", "Diego", "Elena", "Javier", "Irene", "Sergio", "Laura", "Adrián", "Marta", "Raúl",
            "Nerea", "Iván", "Clara", "Óscar");
    private static final List<String> NOTES = List.of(
            "Primera visita.", "Prefiere degradado bajo.", "Viene con su hijo.", "Pregunta por productos para la barba.");

    private final JdbcTemplate jdbc;
    private final AppointmentLinks links;

    DemoSeeder(JdbcTemplate jdbc, AppointmentLinks links) {
        this.jdbc = jdbc;
        this.links = links;
    }

    /** Lo que necesita quien crea la copia para abrir sesión en ella. */
    record SeededDemo(UUID businessId, String slug, UUID ownerUserId, UUID employeeUserId) {
    }

    /** Dentro de una transacción (obligatoria): o se crea la copia entera o nada. */
    SeededDemo seed(Instant now) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The demo must be seeded in a single transaction");
        }
        var random = new Random(CONTENT_SEED);
        ZoneId zone = ZoneId.of(TIMEZONE);
        LocalDate today = LocalDate.ofInstant(now, zone);
        String suffix = randomSuffix();
        String slug = SLUG_PREFIX + suffix;
        OffsetDateTime created = utc(now);

        UUID businessId = newId();
        jdbc.update("""
                INSERT INTO business (id, slug, name, timezone, contact_email, is_demo, created_at)
                VALUES (?, ?, ?, ?, ?, true, ?)""", businessId, slug, BUSINESS_NAME, TIMEZONE, CONTACT_EMAIL, created);

        // Usuarios: un email único por copia (app_user.email es único en toda la plataforma).
        UUID ownerUserId = insertUser("dueno-" + suffix + "@example.com", OWNER_NAME, created);
        UUID employeeUserId = insertUser("empleado-" + suffix + "@example.com", EMPLOYEE_NAME, created);
        jdbc.batchUpdate("INSERT INTO membership (id, business_id, user_id, role, created_at) VALUES (?, ?, ?, ?, ?)",
                List.of(new Object[] {newId(), businessId, ownerUserId, "OWNER", created},
                        new Object[] {newId(), businessId, employeeUserId, "STAFF", created}));

        List<UUID> serviceIds = new ArrayList<>();
        List<Object[]> serviceRows = new ArrayList<>();
        for (ServiceDef s : SERVICES) {
            UUID id = newId();
            serviceIds.add(id);
            serviceRows.add(new Object[] {id, businessId, s.name(), s.description(), s.durationMin(), s.bufferMin(),
                    s.priceCents(), created});
        }
        jdbc.batchUpdate("""
                INSERT INTO service (id, business_id, name, description, duration_min, buffer_min, price_cents, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", serviceRows);

        List<UUID> staffIds = new ArrayList<>();
        List<Object[]> staffRows = new ArrayList<>();
        List<Object[]> staffServiceRows = new ArrayList<>();
        List<Object[]> hourRows = new ArrayList<>();
        for (StaffDef s : STAFF) {
            UUID id = newId();
            staffIds.add(id);
            UUID userId = switch (s.account()) {
                case OWNER -> ownerUserId;
                case EMPLOYEE -> employeeUserId;
                case NONE -> null;
            };
            staffRows.add(new Object[] {id, businessId, userId, s.name(), created});
            s.services().forEach(i -> staffServiceRows.add(new Object[] {businessId, id, serviceIds.get(i)}));
            for (DayOfWeek day : s.days()) {
                for (Range range : hours(day)) {
                    hourRows.add(new Object[] {newId(), businessId, id, day.getValue(), range.start(), range.end()});
                }
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO staff_member (id, business_id, user_id, display_name, created_at)
                VALUES (?, ?, ?, ?, ?)""", staffRows);
        jdbc.batchUpdate("INSERT INTO staff_service (business_id, staff_id, service_id) VALUES (?, ?, ?)",
                staffServiceRows);
        jdbc.batchUpdate("""
                INSERT INTO working_hours (id, business_id, staff_id, day_of_week, start_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?)""", hourRows);

        // Un festivo del negocio dentro de una semana y unos días libres del empleado (los gestiona él mismo).
        LocalDate holiday = nextWeekday(today.plusDays(8));
        LocalDate vacationStart = nextWeekday(today.plusDays(3));
        LocalDate vacationEnd = vacationStart.plusDays(1);
        UUID employeeStaffId = staffIds.get(1);
        jdbc.batchUpdate("""
                INSERT INTO closure (id, business_id, staff_id, start_date, end_date, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                List.of(new Object[] {newId(), businessId, null, holiday, holiday, "Festivo local", created},
                        new Object[] {newId(), businessId, employeeStaffId, vacationStart, vacationEnd, "Vacaciones",
                                created}));

        List<UUID> customerIds = new ArrayList<>();
        List<Object[]> customerRows = new ArrayList<>();
        for (int i = 0; i < FIRST_NAMES.size(); i++) {
            String first = FIRST_NAMES.get(i);
            String last = i % 2 == 0 ? "Ejemplo" : "Prueba";
            UUID id = newId();
            customerIds.add(id);
            customerRows.add(new Object[] {id, businessId, ascii(first) + "." + ascii(last) + "@example.com",
                    first + " " + last, created});
        }
        jdbc.batchUpdate("INSERT INTO customer (id, business_id, email, full_name, created_at) VALUES (?, ?, ?, ?, ?)",
                customerRows);

        List<Object[]> appointments = new ArrayList<>();
        for (int s = 0; s < STAFF.size(); s++) {
            StaffDef def = STAFF.get(s);
            List<Object[]> own = new ArrayList<>();
            for (LocalDate day = today.minusDays(DAYS_BACK); !day.isAfter(today.plusDays(DAYS_AHEAD)); day = day.plusDays(1)) {
                boolean absent = s == 1 && !day.isBefore(vacationStart) && !day.isAfter(vacationEnd);
                if (!def.days().contains(day.getDayOfWeek()) || day.equals(holiday) || absent) {
                    continue;
                }
                for (Range range : hours(day.getDayOfWeek())) {
                    LocalTime t = range.start();
                    while (true) {
                        ServiceDef service = SERVICES.get(def.services().get(random.nextInt(def.services().size())));
                        LocalTime end = t.plusMinutes(service.durationMin());
                        LocalTime blocked = end.plusMinutes(service.bufferMin());
                        if (blocked.isAfter(range.end()) || blocked.isBefore(t)) {
                            break;
                        }
                        if (random.nextDouble() < occupancy(today, day)) {
                            own.add(appointment(random, now, zone, businessId, staffIds.get(s),
                                    serviceIds.get(SERVICES.indexOf(service)),
                                    customerIds.get(random.nextInt(customerIds.size())), day, t, end, blocked));
                            t = roundUpToQuarter(blocked);
                        } else {
                            t = t.plusMinutes(30);
                        }
                    }
                }
            }
            if (def.account() == Account.EMPLOYEE) {
                leaveOneUnmarked(own, now);
            }
            appointments.addAll(own);
        }
        jdbc.batchUpdate("""
                INSERT INTO appointment (id, business_id, staff_id, service_id, customer_id, starts_at, ends_at,
                                         blocked_until, status, manage_token_hash, notes, created_at, cancelled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", appointments);

        return new SeededDemo(businessId, slug, ownerUserId, employeeUserId);
    }

    /** Ocupación: mucha en el pasado y en los próximos días, menos más adelante (quedan huecos para reservar). */
    private static double occupancy(LocalDate today, LocalDate day) {
        long ahead = day.toEpochDay() - today.toEpochDay();
        if (ahead <= 0) {
            return 0.7;
        }
        return ahead <= 3 ? 0.55 : 0.35;
    }

    // Índices de las columnas de una fila de cita (para retocar el estado después).
    private static final int STARTS_AT = 5;
    private static final int STATUS = 8;
    private static final int CANCELLED_AT = 12;

    private Object[] appointment(Random random, Instant now, ZoneId zone, UUID businessId, UUID staffId,
            UUID serviceId, UUID customerId, LocalDate day, LocalTime start, LocalTime end, LocalTime blocked) {
        Instant startsAt = day.atTime(start).atZone(zone).toInstant();
        Instant endsAt = day.atTime(end).atZone(zone).toInstant();
        Instant blockedUntil = day.atTime(blocked).atZone(zone).toInstant();
        // Se reservó entre 1 y 10 días antes (y nunca «en el futuro»).
        Instant createdAt = startsAt.minus(Duration.ofHours(24 + random.nextInt(24 * 9)));
        if (createdAt.isAfter(now)) {
            createdAt = now.minus(Duration.ofHours(1 + random.nextInt(72)));
        }
        AppointmentStatus status = status(random, now, startsAt);
        Instant cancelledAt = null;
        if (status == AppointmentStatus.CANCELLED) {
            Instant limit = startsAt.isBefore(now) ? startsAt : now;
            long window = Math.max(1, Duration.between(createdAt, limit).toMinutes());
            cancelledAt = createdAt.plus(Duration.ofMinutes((long) (random.nextDouble() * window)));
        }
        String notes = random.nextInt(10) == 0 ? NOTES.get(random.nextInt(NOTES.size())) : null;
        UUID id = newId();
        return new Object[] {id, businessId, staffId, serviceId, customerId, utc(startsAt), utc(endsAt),
                utc(blockedUntil), status.name(), links.tokenHash(id), notes, utc(createdAt), utcOrNull(cancelledAt)};
    }

    private static AppointmentStatus status(Random random, Instant now, Instant startsAt) {
        int dice = random.nextInt(100);
        if (!startsAt.isBefore(now)) {
            return dice < 6 ? AppointmentStatus.CANCELLED : AppointmentStatus.CONFIRMED;
        }
        if (!startsAt.isBefore(now.minus(RECENT))) {
            // Recientes: casi todas sin marcar, para que se puedan marcar en la agenda.
            if (dice < 5) {
                return AppointmentStatus.CANCELLED;
            }
            return dice < 30 ? AppointmentStatus.COMPLETED : AppointmentStatus.CONFIRMED;
        }
        if (dice < 8) {
            return AppointmentStatus.CANCELLED;
        }
        return dice < 15 ? AppointmentStatus.NO_SHOW : AppointmentStatus.COMPLETED;
    }

    /**
     * Garantiza que el empleado tiene al menos una cita empezada sin marcar (con su vista se prueba
     * «Marcar completada / no presentada»), sea cual sea la hora a la que se crea la copia.
     */
    private static void leaveOneUnmarked(List<Object[]> rows, Instant now) {
        boolean hasOne = rows.stream().anyMatch(r -> startedAt(r).isBefore(now)
                && AppointmentStatus.CONFIRMED.name().equals(r[STATUS]));
        if (hasOne) {
            return;
        }
        rows.stream()
                .filter(r -> startedAt(r).isBefore(now) && !AppointmentStatus.CANCELLED.name().equals(r[STATUS]))
                .reduce((first, second) -> second) // la más reciente (las filas van en orden)
                .ifPresent(r -> {
                    r[STATUS] = AppointmentStatus.CONFIRMED.name();
                    r[CANCELLED_AT] = null;
                });
    }

    private static Instant startedAt(Object[] row) {
        return ((OffsetDateTime) row[STARTS_AT]).toInstant();
    }

    private UUID insertUser(String email, String fullName, OffsetDateTime created) {
        UUID id = newId();
        jdbc.update("INSERT INTO app_user (id, email, password_hash, full_name, created_at) VALUES (?, ?, ?, ?, ?)",
                id, email, NO_PASSWORD, fullName, created);
        return id;
    }

    private static LocalDate nextWeekday(LocalDate from) {
        LocalDate day = from;
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static LocalTime roundUpToQuarter(LocalTime time) {
        int minutes = time.getHour() * 60 + time.getMinute();
        int rounded = (minutes + 14) / 15 * 15;
        return rounded >= 24 * 60 ? LocalTime.MAX : LocalTime.of(rounded / 60, rounded % 60);
    }

    private static String randomSuffix() {
        var sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(SLUG_ALPHABET.charAt(SLUGS.nextInt(SLUG_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Para los emails: sin tildes ni mayúsculas («Álvaro» → «alvaro»). */
    private static String ascii(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static UUID newId() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static @Nullable OffsetDateTime utcOrNull(@Nullable Instant instant) {
        return instant == null ? null : utc(instant);
    }
}
