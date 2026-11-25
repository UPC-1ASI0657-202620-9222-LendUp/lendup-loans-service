package com.lendup.prestamos.application.port.out;
import com.lendup.prestamos.domain.event.EventEnvelope;
public interface OutboxPort { void append(EventEnvelope event); }
