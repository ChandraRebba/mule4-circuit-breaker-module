package org.mule.extension.circuitbreaker.internal;

import org.mule.runtime.extension.api.exception.ModuleException;

public class CircuitBreakerException extends ModuleException {
    public CircuitBreakerException(String message) {
        super(CircuitBreakerError.CIRCUIT_OPEN, new Exception(message));
    }
}
