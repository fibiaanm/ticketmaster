package com.tickets.adapter.out.ai;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.application.port.out.AnalizadorTicketService;
import com.tickets.domain.Categoria;
import com.tickets.domain.OrigenAnalisis;
import com.tickets.domain.Prioridad;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Opcion B: clasificador local por reglas y palabras clave. Siempre disponible (respaldo del LLM). */
public class ReglasAnalizador implements AnalizadorTicketService {

    static final int MAX_PALABRAS_RESUMEN = 30;
    static final int MAX_ETIQUETAS = 5;
    static final String MODELO = "reglas-v1";

    private record ReglaCategoria(Categoria categoria, List<String> palabras) {
    }

    /** aportaEtiquetas: las palabras que disparan la regla pasan a ser etiquetas del ticket. */
    private record ReglaPrioridad(Prioridad prioridad, List<String> palabras, boolean aportaEtiquetas) {
    }

    private record CategoriaDetectada(Categoria categoria, List<String> palabras) {
    }

    private record PrioridadDetectada(Prioridad prioridad, List<String> etiquetas) {
    }

    /** Se evalua en este orden; ante empate gana la primera. */
    private static final List<ReglaCategoria> REGLAS_CATEGORIA = List.of(
            new ReglaCategoria(Categoria.CONECTIVIDAD, List.of("internet", "wifi", "wi-fi", "red", "vpn",
                    "conexión", "conectarme", "conectar", "router", "ethernet")),
            new ReglaCategoria(Categoria.ACCESOS, List.of("contraseña", "password", "clave", "login", "permiso",
                    "permisos", "acceso", "sesión", "cuenta", "credenciales", "bloqueada")),
            new ReglaCategoria(Categoria.HARDWARE, List.of("pantalla", "teclado", "computador", "computadora",
                    "laptop", "portátil", "mouse", "ratón", "impresora", "monitor", "cargador", "batería", "equipo")),
            new ReglaCategoria(Categoria.SOFTWARE, List.of("programa", "aplicación", "app", "error", "software",
                    "instalar", "instalación", "actualización", "sistema", "excel", "outlook", "falla")));

    /** Orden de precedencia: lo critico gana a lo bajo, y lo bajo gana a lo alto ("no es urgente"). */
    private static final List<ReglaPrioridad> REGLAS_PRIORIDAD = List.of(
            new ReglaPrioridad(Prioridad.CRITICA, List.of("producción detenida", "todos los usuarios", "caída total",
                    "sistema caído", "nadie puede", "toda la empresa"), true),
            new ReglaPrioridad(Prioridad.BAJA, List.of("no es urgente", "sin prisa", "cuando puedas",
                    "cuando tengan tiempo", "sugerencia", "duda"), false),
            new ReglaPrioridad(Prioridad.ALTA, List.of("urgente", "urgencia", "reunión", "importante", "no puedo",
                    "inmediato", "cuanto antes", "lo antes posible", "cliente"), true));

    @Override
    public AnalisisTicketResponse analizar(String titulo, String descripcion) {
        String texto = normalizar(titulo + " " + descripcion);
        CategoriaDetectada categoria = detectarCategoria(texto);
        PrioridadDetectada prioridad = detectarPrioridad(texto);

        return new AnalisisTicketResponse(categoria.categoria().name(), prioridad.prioridad().name(),
                resumir(titulo, descripcion), etiquetas(categoria, prioridad), OrigenAnalisis.REGLAS, MODELO, null);
    }

    private static CategoriaDetectada detectarCategoria(String texto) {
        CategoriaDetectada mejor = new CategoriaDetectada(Categoria.OTRO, List.of());
        for (ReglaCategoria regla : REGLAS_CATEGORIA) {
            List<String> encontradas = encontradas(texto, regla.palabras());
            if (encontradas.size() > mejor.palabras().size()) {
                mejor = new CategoriaDetectada(regla.categoria(), encontradas);
            }
        }
        return mejor;
    }

    private static PrioridadDetectada detectarPrioridad(String texto) {
        for (ReglaPrioridad regla : REGLAS_PRIORIDAD) {
            List<String> encontradas = encontradas(texto, regla.palabras());
            if (!encontradas.isEmpty()) {
                return new PrioridadDetectada(regla.prioridad(), regla.aportaEtiquetas() ? encontradas : List.of());
            }
        }
        return new PrioridadDetectada(Prioridad.MEDIA, List.of());
    }

    private static List<String> etiquetas(CategoriaDetectada categoria, PrioridadDetectada prioridad) {
        Set<String> etiquetas = new LinkedHashSet<>();
        categoria.palabras().stream().limit(2).forEach(etiquetas::add);
        etiquetas.add(categoria.categoria() == Categoria.OTRO ? "general" : categoria.categoria().name().toLowerCase());
        prioridad.etiquetas().stream().limit(2).forEach(etiquetas::add);
        return new ArrayList<>(etiquetas).subList(0, Math.min(MAX_ETIQUETAS, etiquetas.size()));
    }

    private static List<String> encontradas(String textoNormalizado, List<String> palabras) {
        return palabras.stream().filter(p -> contiene(textoNormalizado, p)).toList();
    }

    /** Coincidencia por palabra completa y sin distinguir tildes ni mayusculas ("red" no coincide con "redactar"). */
    private static boolean contiene(String textoNormalizado, String palabra) {
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(normalizar(palabra)) + "(?![a-z0-9])")
                .matcher(textoNormalizado).find();
    }

    private static String resumir(String titulo, String descripcion) {
        String base = Optional.ofNullable(descripcion).filter(d -> !d.isBlank()).orElse(titulo).strip();
        String primeraFrase = base.split("(?<=[.!?])\\s+|\\n", 2)[0].strip();
        String[] palabras = primeraFrase.split("\\s+");
        return String.join(" ", Arrays.copyOf(palabras, Math.min(palabras.length, MAX_PALABRAS_RESUMEN)));
    }

    static String normalizar(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
