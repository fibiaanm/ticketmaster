package com.tickets.domain;

/** Quien ejecuta una accion (identidad autenticada). */
public record Actor(Long id, Rol rol) {
}
