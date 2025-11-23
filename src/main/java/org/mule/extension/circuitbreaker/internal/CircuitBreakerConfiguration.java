package org.mule.extension.circuitbreaker.internal;

import org.mule.runtime.extension.api.annotation.Operations;

@Operations(CircuitBreakerOperations.class)
public class CircuitBreakerConfiguration {
    // Configuration is now handled at the Operations level with @Parameter
    // State is managed via static registry in CircuitBreakerState (scopes cannot inject @Config)
}
