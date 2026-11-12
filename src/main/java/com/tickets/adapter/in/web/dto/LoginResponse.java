package com.tickets.adapter.in.web.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, Instant accessTokenExpiraEn, String sessionToken,
                            Instant sessionExpiraEn, Long usuarioId, String nombre, String rol) {
}
