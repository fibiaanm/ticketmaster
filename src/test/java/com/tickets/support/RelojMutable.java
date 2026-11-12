package com.tickets.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj controlable para probar expiraciones sin dormir. */
public class RelojMutable extends Clock {
    private Instant ahora;

    public RelojMutable(Instant inicio) { this.ahora = inicio; }

    public void avanzar(Duration d) { ahora = ahora.plus(d); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return ahora; }
}
