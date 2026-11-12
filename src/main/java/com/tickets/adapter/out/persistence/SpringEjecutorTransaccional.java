package com.tickets.adapter.out.persistence;

import com.tickets.application.port.out.EjecutorTransaccional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
class SpringEjecutorTransaccional implements EjecutorTransaccional {

    private final TransactionTemplate plantilla;

    SpringEjecutorTransaccional(PlatformTransactionManager manager) {
        this.plantilla = new TransactionTemplate(manager);
    }

    @Override
    public <T> T ejecutar(Supplier<T> bloque) {
        return plantilla.execute(status -> bloque.get());
    }
}
