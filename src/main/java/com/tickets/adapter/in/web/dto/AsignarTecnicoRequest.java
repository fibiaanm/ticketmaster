package com.tickets.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record AsignarTecnicoRequest(@NotNull(message = "El tecnicoId es obligatorio") Long tecnicoId) {
}
