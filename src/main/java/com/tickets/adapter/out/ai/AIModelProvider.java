package com.tickets.adapter.out.ai;

/** Estrategia: un proveedor de modelo de lenguaje (OpenAI, otro...). */
public interface AIModelProvider {
    String nombre();

    AIRespuesta generar(String systemPrompt, String instrucciones);

    record AIRespuesta(String contenido, String modelo) {
    }
}
