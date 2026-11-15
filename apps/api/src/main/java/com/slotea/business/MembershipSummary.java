package com.slotea.business;

import java.util.UUID;

/**
 * Un negocio al que pertenece el usuario y su rol en él. {@code demo}: es una copia de la demo (la web
 * muestra la barra de la demo y la bandeja). {@code demoCanSwitch}: en esa copia hay una cuenta con el
 * otro rol, así que se puede pasar de dueño a empleado o al revés (en psicología no hay empleado).
 */
public record MembershipSummary(UUID businessId, String slug, String name, Role role, boolean demo,
        boolean demoCanSwitch) {
}
