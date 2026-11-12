package com.slotea.business;

import java.util.UUID;

/** Un negocio al que pertenece el usuario y su rol en él. */
public record MembershipSummary(UUID businessId, String slug, String name, Role role) {
}
