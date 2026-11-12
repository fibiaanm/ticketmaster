package com.tickets.adapter.in.web;

import com.tickets.adapter.in.web.dto.LoginRequest;
import com.tickets.adapter.in.web.dto.LoginResponse;
import com.tickets.application.port.in.AutenticarUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AutenticarUseCase autenticar;

    public AuthController(AutenticarUseCase autenticar) {
        this.autenticar = autenticar;
    }

    @PostMapping("/login")
    @Operation(summary = "Login: devuelve un JWT (Authorization: Bearer) y un token de sesión (X-Ticket-Auth)")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var r = autenticar.login(request.correo(), request.password());
        return new LoginResponse(r.accessToken(), r.accessTokenExpiraEn(), r.sessionToken(), r.sessionExpiraEn(),
                r.usuarioId(), r.nombre(), r.rol().name());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoca el token de sesión enviado en X-Ticket-Auth")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-Ticket-Auth", required = false) String sesion) {
        autenticar.cerrarSesion(sesion);
        return ResponseEntity.noContent().build();
    }
}
