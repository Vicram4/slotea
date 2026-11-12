package com.slotea.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.slotea.auth.AuthDtos.MeResponse;
import com.slotea.auth.AuthDtos.SignupRequest;
import com.slotea.auth.AuthDtos.UserView;
import com.slotea.business.Business;
import com.slotea.business.BusinessRepository;
import com.slotea.business.Membership;
import com.slotea.business.MembershipRepository;
import com.slotea.business.MembershipSummary;
import com.slotea.business.Role;
import com.slotea.shared.security.OpaqueTokens;
import com.slotea.shared.tenancy.SystemTransactions;
import com.slotea.shared.web.ApiException;

/**
 * Registro, login, refresh y logout. Todo cruza negocios (un usuario puede estar en varios), así que
 * cada operación corre en una {@link SystemTransactions} explícita.
 */
@Service
public class AuthService {

    static final String DEFAULT_TIMEZONE = "Europe/Madrid";

    /** El slug es la URL pública /{slug}: no puede chocar con rutas de la web. */
    static final Set<String> RESERVED_SLUGS = Set.of(
            "api", "app", "panel", "login", "logout", "registro", "signup", "cita", "citas", "admin",
            "demo", "ayuda", "help", "soporte", "precios", "legal", "privacidad", "terminos", "cookies",
            "static", "public", "assets", "www", "slotea");

    private static final String INVALID_CREDENTIALS = "Email o contraseña incorrectos.";
    private static final String INVALID_SESSION = "La sesión ha caducado. Vuelve a iniciar sesión.";

    private final AppUserRepository users;
    private final BusinessRepository businesses;
    private final MembershipRepository memberships;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenService accessTokens;
    private final PasswordEncoder passwordEncoder;
    private final SystemTransactions system;
    private final AuthProperties properties;
    private final Clock clock;
    /** Si el email no existe comparamos igualmente contra este hash: mismo tiempo de respuesta. */
    private final String dummyPasswordHash;

    AuthService(AppUserRepository users, BusinessRepository businesses, MembershipRepository memberships,
            RefreshTokenRepository refreshTokens, AccessTokenService accessTokens, PasswordEncoder passwordEncoder,
            SystemTransactions system, AuthProperties properties, Clock clock) {
        this.users = users;
        this.businesses = businesses;
        this.memberships = memberships;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.system = system;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(OpaqueTokens.generate());
    }

    /** Resultado de una operación que abre sesión: tokens para las cookies y el «quién soy». */
    record Session(AuthTokens tokens, MeResponse me) {
    }

    Session signup(SignupRequest request) {
        // BCrypt solo usa los primeros 72 bytes (no caracteres): rechazamos en vez de truncar.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("La contraseña es demasiado larga.");
        }
        String email = AppUser.normalizeEmail(request.email());
        String slug = request.slug();
        return system.execute(() -> {
            if (RESERVED_SLUGS.contains(slug) || businesses.existsBySlug(slug)) {
                throw ApiException.conflict("Esa dirección ya está en uso. Prueba con otra.");
            }
            if (users.existsByEmail(email)) {
                throw ApiException.conflict("Ya existe una cuenta con ese email.");
            }
            Instant now = Instant.now(clock);
            String timezone = request.timezone() != null ? request.timezone() : DEFAULT_TIMEZONE;
            var business = businesses.save(new Business(slug, request.businessName().strip(), timezone, email, now));
            var user = users.save(new AppUser(email, passwordEncoder.encode(request.password()),
                    request.fullName().strip(), now));
            memberships.save(Membership.forBusiness(business.getId(), user, Role.OWNER, now));
            // Sin flush, un slug o email duplicado por una carrera fallaría al hacer commit, fuera de aquí.
            memberships.flush();
            return startSession(user, UUID.randomUUID(), null);
        });
    }

    Session login(String rawEmail, String password) {
        String email = AppUser.normalizeEmail(rawEmail);
        return system.execute(() -> {
            Optional<AppUser> user = users.findByEmail(email);
            boolean matches = passwordEncoder.matches(password,
                    user.map(AppUser::getPasswordHash).orElse(dummyPasswordHash));
            if (user.isEmpty() || !matches) {
                throw ApiException.unauthorized(INVALID_CREDENTIALS);
            }
            refreshTokens.deleteExpiredForUser(user.get().getId(), Instant.now(clock));
            return startSession(user.get(), UUID.randomUUID(), null);
        });
    }

    /**
     * Rota el refresh token. Si el token ya se había usado (o dos peticiones lo usan a la vez), se
     * revoca toda la familia: o alguien lo ha robado, o el cliente se ha desincronizado.
     */
    Session refresh(@Nullable String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized(INVALID_SESSION);
        }
        byte[] hash = OpaqueTokens.sha256(rawToken);
        Optional<Session> session = system.execute(() -> {
            Instant now = Instant.now(clock);
            RefreshToken current = refreshTokens.findByTokenHash(hash).orElse(null);
            if (current == null || current.isExpired(now)) {
                return Optional.empty();
            }
            if (current.getRevokedAt() != null || refreshTokens.revokeIfActive(current.getId(), now) == 0) {
                refreshTokens.revokeFamily(current.getFamilyId(), now);
                return Optional.empty();
            }
            AppUser user = users.findById(current.getUserId()).orElseThrow();
            NewSession next = openSession(user, current.getFamilyId(), current.getBusinessId());
            refreshTokens.markReplaced(current.getId(), next.refreshTokenId());
            return Optional.of(next.session());
        });
        // Fuera de la transacción: la revocación de la familia tiene que quedar guardada.
        return session.orElseThrow(() -> ApiException.unauthorized(INVALID_SESSION));
    }

    /** Cierra la sesión de verdad: el refresh token (y toda su familia) deja de servir. */
    void logout(@Nullable String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        byte[] hash = OpaqueTokens.sha256(rawToken);
        system.execute(() -> {
            refreshTokens.findByTokenHash(hash)
                    .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now(clock)));
            return null;
        });
    }

    /** «Quién soy» para un usuario autenticado; {@code activeBusinessId} viene del JWT. */
    MeResponse me(UUID userId, @Nullable UUID activeBusinessId) {
        return system.execute(() -> {
            AppUser user = users.findById(userId)
                    .orElseThrow(() -> ApiException.unauthorized(INVALID_SESSION));
            List<MembershipSummary> all = memberships.findSummariesByUserId(userId);
            return toMe(user, all, find(all, activeBusinessId));
        });
    }

    /**
     * Crea un refresh token nuevo y su JWT. Las membresías se leen de nuevo en cada refresh: si al
     * usuario le quitan el acceso a un negocio, lo pierde como mucho cuando caduque el JWT (15 min).
     */
    private Session startSession(AppUser user, UUID familyId, @Nullable UUID preferredBusinessId) {
        return openSession(user, familyId, preferredBusinessId).session();
    }

    private record NewSession(Session session, UUID refreshTokenId) {
    }

    private NewSession openSession(AppUser user, UUID familyId, @Nullable UUID preferredBusinessId) {
        List<MembershipSummary> all = memberships.findSummariesByUserId(user.getId());
        MembershipSummary active = find(all, preferredBusinessId);
        if (active == null && !all.isEmpty()) {
            active = all.getFirst();
        }
        Instant now = Instant.now(clock);
        String refreshToken = OpaqueTokens.generate();
        RefreshToken saved = refreshTokens.save(new RefreshToken(user.getId(), familyId,
                OpaqueTokens.sha256(refreshToken), active != null ? active.businessId() : null,
                now, now.plus(properties.refreshTokenTtl())));
        String accessToken = accessTokens.issue(user.getId(), active);
        return new NewSession(new Session(new AuthTokens(accessToken, refreshToken), toMe(user, all, active)),
                saved.getId());
    }

    private static @Nullable MembershipSummary find(List<MembershipSummary> all, @Nullable UUID businessId) {
        if (businessId == null) {
            return null;
        }
        return all.stream().filter(m -> m.businessId().equals(businessId)).findFirst().orElse(null);
    }

    private static MeResponse toMe(AppUser user, List<MembershipSummary> all, @Nullable MembershipSummary active) {
        return new MeResponse(new UserView(user.getId(), user.getEmail(), user.getFullName()), active, all);
    }
}
