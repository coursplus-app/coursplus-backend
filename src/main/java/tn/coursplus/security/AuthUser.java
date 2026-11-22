package tn.coursplus.security;

import tn.coursplus.entity.Role;

/** L'utilisateur connecté pour la requête en cours (stocké dans le SecurityContext). */
public record AuthUser(Long id, Role role) {
}
