package com.tickets.adapter.out.prompt;

import com.tickets.application.port.out.PromptDefinicion;
import com.tickets.application.port.out.PromptSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee prompts de classpath:prompts/{nombre}/v{N}/system.txt y user.txt. */
@Component
public class ClasspathPromptSource implements PromptSource {

    private static final Pattern RUTA = Pattern.compile(".*/prompts/([^/]+)/v(\\d+)/system\\.txt$");

    @Override
    public List<PromptDefinicion> cargar() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            List<PromptDefinicion> resultado = new ArrayList<>();
            for (Resource system : resolver.getResources("classpath*:prompts/*/v*/system.txt")) {
                Matcher m = RUTA.matcher(system.getURL().toString());
                if (!m.matches()) {
                    continue;
                }
                Resource user = system.createRelative("user.txt");
                resultado.add(new PromptDefinicion(m.group(1), Integer.parseInt(m.group(2)),
                        leer(system), leer(user)));
            }
            return resultado;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudieron cargar los prompts", e);
        }
    }

    private String leer(Resource r) throws IOException {
        return new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
    }
}
