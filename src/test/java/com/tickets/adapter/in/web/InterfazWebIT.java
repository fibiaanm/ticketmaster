package com.tickets.adapter.in.web;

import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** La interfaz web es estatica y publica; la API sigue exigiendo credenciales. */
class InterfazWebIT extends ApiTestBase {

    @Test
    void laPaginaPrincipalEsPublicaYSeSirveEnLaRaiz() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        var respuesta = mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html")).andReturn().getResponse();
        // el HTML declara su codificacion con <meta charset>, asi que se lee explicitamente como UTF-8
        String html = respuesta.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(html).contains("Gestión de tickets").contains("charset=\"utf-8\"")
                // sin scripts ni estilos en linea: la CSP los bloquearia
                .doesNotContain("<script>").doesNotContain("style=");
    }

    @Test
    void losRecursosEstaticosSonPublicos() throws Exception {
        mvc.perform(get("/assets/app.js")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(get("/assets/app.css")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void laPaginaLlevaUnaCspEstrictaYNoElResto() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/assets/app.js")).andExpect(header().exists("Content-Security-Policy"));
        // Swagger UI necesita sus propios scripts: no se le aplica la CSP de la pagina
        mvc.perform(get("/v3/api-docs")).andExpect(header().doesNotExist("Content-Security-Policy"));
    }

    @Test
    void laApiSigueProtegida() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
    }

    @Test
    void elJavaScriptNoUsaApisPeligrosasParaInsertarHtml() throws Exception {
        String js = mvc.perform(get("/assets/app.js")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(js)
                .doesNotContain("innerHTML").doesNotContain("outerHTML").doesNotContain("insertAdjacentHTML")
                .doesNotContain("document.write").doesNotContain("eval(").doesNotContain("localStorage");
    }
}
