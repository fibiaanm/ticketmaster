package com.tickets.application.port.out;

import java.util.List;

/** Fuente de definiciones de prompts (archivos del repo). */
public interface PromptSource {
    List<PromptDefinicion> cargar();
}
