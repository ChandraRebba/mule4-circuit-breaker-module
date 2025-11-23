package org.mule.extension.circuitbreaker.internal;

import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

import java.util.Optional;

public enum CircuitBreakerError implements ErrorTypeDefinition<CircuitBreakerError> {
    CIRCUIT_OPEN;

    @Override
    public Optional<ErrorTypeDefinition<? extends Enum<?>>> getParent() {
        return Optional.empty();
    }
}
