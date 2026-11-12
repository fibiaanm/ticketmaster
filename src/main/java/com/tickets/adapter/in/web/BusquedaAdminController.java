package com.tickets.adapter.in.web;

import com.tickets.application.port.in.ReindexarBusquedaUseCase;
import com.tickets.domain.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/busqueda")
@Tag(name = "Administración de búsqueda")
public class BusquedaAdminController {

    private final ReindexarBusquedaUseCase reindexar;

    public BusquedaAdminController(ReindexarBusquedaUseCase reindexar) {
        this.reindexar = reindexar;
    }

    @PostMapping("/reindexar")
    @Operation(summary = "Reconstruye el índice de OpenSearch desde MySQL (ADMIN). Responde 202: lo procesa el worker")
    public ResponseEntity<Map<String, Object>> reindexar(@AuthenticationPrincipal Actor actor,
                                                         @RequestParam(defaultValue = "false") boolean recrear) {
        int programadas = reindexar.reindexar(actor, recrear);
        return ResponseEntity.accepted().body(Map.of("programadas", programadas, "recrearIndice", recrear));
    }
}
