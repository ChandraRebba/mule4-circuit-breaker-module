package org.mule.extension.circuitbreaker.internal;

import org.mule.runtime.extension.api.annotation.error.ErrorTypeProvider;
import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

import java.util.HashSet;
import java.util.Set;

public class CircuitBreakerErrorProvider implements ErrorTypeProvider {
    @Override
    public Set<ErrorTypeDefinition> getErrorTypes() {
        Set<ErrorTypeDefinition> errors = new HashSet<>();
        errors.add(CircuitBreakerError.CIRCUIT_OPEN);
        return errors;
    }
}
