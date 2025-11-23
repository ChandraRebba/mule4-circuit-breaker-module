package org.mule.extension.circuitbreaker.internal;

import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.error.Throws;
import org.mule.runtime.extension.api.annotation.param.MediaType;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.mule.runtime.extension.api.runtime.process.CompletionCallback;
import org.mule.runtime.extension.api.runtime.route.Chain;

import java.util.concurrent.TimeUnit;

import java.util.HashSet;
import java.util.Set;
import org.mule.runtime.extension.api.exception.ModuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mule.runtime.extension.api.annotation.param.MediaType.ANY;

public class CircuitBreakerOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerOperations.class);

    // State is managed by the Configuration

    @Parameter
    @DisplayName("Failure Threshold")
    @Summary("Number of failures before opening the circuit")
    @Optional(defaultValue = "5")
    private int failureThreshold;

    @Parameter
    @DisplayName("Timeout (minutes)")
    @Summary("Time to wait before attempting to close the circuit")
    @Optional(defaultValue = "5")
    private int timeoutMinutes;

    @Parameter
    @DisplayName("Half-Open Attempts")
    @Summary("Number of successful calls required to close the circuit")
    @Optional(defaultValue = "3")
    private int halfOpenAttempts;

    @Parameter
    @DisplayName("Included Errors")
    @Summary("Comma-separated list of specific error types to track. If empty, all errors are tracked.")
    @Optional
    private String includedErrors;

    @Parameter
    @DisplayName("Circuit Name")
    @Summary("Unique name for this circuit breaker instance to share state.")
    @Optional(defaultValue = "default-circuit")
    private String circuitName;

    @Alias("filter")
    @MediaType(value = ANY, strict = false)
    @Throws(CircuitBreakerErrorProvider.class)
    public void filter(Chain operations,
                       CompletionCallback<Object, Object> callback) {

        // Use shared state from static registry (scopes cannot inject @Config)
        CircuitBreakerState state = CircuitBreakerState.get(circuitName);

        // Create a simple config wrapper to pass values to state
        CircuitBreakerConfig config = new CircuitBreakerConfig(failureThreshold, timeoutMinutes, halfOpenAttempts);
        
        // Check if we can transition from OPEN to HALF_OPEN
        state.checkState(config);

        if (state.getCurrentState() == CircuitBreakerState.State.OPEN) {
            // Fail fast
            callback.error(new CircuitBreakerException("Circuit is OPEN. Downstream is unavailable."));
            return;
        }

        // Execute the chain
        operations.process(
            result -> {
                state.recordSuccess(config);
                callback.success(result);
            },
            (error, previous) -> {
                if (shouldRecordFailure(error)) {
                    state.recordFailure(config);
                }
                callback.error(error);
            }
        );
    }

    private boolean shouldRecordFailure(Throwable error) {
        Set<String> currentErrorTypes = getErrorTypes(error);
        
        // Debug logging
        LOGGER.debug("Checking error against circuit breaker. Found types: {}", currentErrorTypes);
        
        if (includedErrors != null && !includedErrors.trim().isEmpty()) {
            String[] included = includedErrors.split(",");
            boolean matchFound = false;
            for (String inc : included) {
                String trimmedInc = inc.trim();
                // Check exact match
                if (currentErrorTypes.contains(trimmedInc)) {
                    LOGGER.debug("Match found for included error: {}", trimmedInc);
                    matchFound = true;
                    break;
                }
                
                // Check if config is NAMESPACE:IDENTIFIER and we have IDENTIFIER in detected types
                // This handles cases where we can't extract the namespace from the exception (e.g. HttpError enum)
                if (trimmedInc.contains(":")) {
                    String identifier = trimmedInc.substring(trimmedInc.indexOf(":") + 1);
                    if (currentErrorTypes.contains(identifier)) {
                        LOGGER.debug("Match found for included error (Identifier match): {} matches detected {}", trimmedInc, identifier);
                        matchFound = true;
                        break;
                    }
                }
            }
            
            if (!matchFound) {
                LOGGER.debug("No match found in included errors: {}", includedErrors);
                return false;
            }
        }
        
        return true;
    }

    private Set<String> getErrorTypes(Throwable error) {
        Set<String> types = new HashSet<>();
        
        Throwable current = error;
        int depth = 0;
        // Traverse cause chain to find all relevant error types
        while (current != null && depth < 10) {
            types.add(current.getClass().getName());
            LOGGER.debug("Traversing exception depth {}: {}", depth, current.getClass().getName());
            
            if (current instanceof ModuleException) {
                // Use Object to avoid dependency issues with ErrorType
                Object type = ((ModuleException) current).getType();
                if (type != null) {
                    types.add(type.toString()); 
                    LOGGER.debug("Found ModuleException type: {}", type.toString());
                    
                    // Try to get namespace and identifier via reflection
                    try {
                         java.lang.reflect.Method getNamespace = type.getClass().getMethod("getNamespace");
                         java.lang.reflect.Method getIdentifier = type.getClass().getMethod("getIdentifier");
                         
                         Object namespace = getNamespace.invoke(type);
                         Object identifier = getIdentifier.invoke(type);
                         
                         LOGGER.debug("ModuleException Namespace: {}, Identifier: {}", namespace, identifier);
                         
                         if (namespace != null && identifier != null) {
                             types.add(namespace.toString() + ":" + identifier.toString());
                         }
                    } catch (Exception inner) {
                        LOGGER.debug("Failed to reflect on ModuleException type: {}", inner.getMessage());
                    }
                }
            }
            
            // Try reflection for getType() as some Mule exceptions might have it but not extend ModuleException
            try {
                 java.lang.reflect.Method getType = current.getClass().getMethod("getType");
                 Object type = getType.invoke(current);
                 if (type != null) {
                     types.add(type.toString());
                     LOGGER.debug("Found reflected getType(): {} (Class: {})", type.toString(), type.getClass().getName());
                     
                     // Try to get namespace and identifier via reflection
                     try {
                         java.lang.reflect.Method getNamespace = type.getClass().getMethod("getNamespace");
                         java.lang.reflect.Method getIdentifier = type.getClass().getMethod("getIdentifier");
                         
                         Object namespace = getNamespace.invoke(type);
                         Object identifier = getIdentifier.invoke(type);
                         
                         LOGGER.debug("Reflected Namespace: {}, Identifier: {}", namespace, identifier);
                         
                         if (namespace != null && identifier != null) {
                             types.add(namespace.toString() + ":" + identifier.toString());
                         }
                     } catch (Exception inner) {
                         LOGGER.debug("Failed to reflect on getType result: {}", inner.getMessage());
                     }
                 }
            } catch (Exception e) {
                // ignore
            }
            
            current = current.getCause();
            depth++;
        }
        
        return types;
    }

    // Config holder to pass values to state
    static class CircuitBreakerConfig {
        private final int failureThreshold;
        private final long timeoutMillis;
        private final int halfOpenAttempts;

        CircuitBreakerConfig(int failureThreshold, int timeoutMinutes, int halfOpenAttempts) {
            this.failureThreshold = failureThreshold;
            this.timeoutMillis = TimeUnit.MINUTES.toMillis(timeoutMinutes);
            this.halfOpenAttempts = halfOpenAttempts;
        }

        int getFailureThreshold() {
            return failureThreshold;
        }

        long getTimeoutMillis() {
            return timeoutMillis;
        }

        int getHalfOpenAttempts() {
            return halfOpenAttempts;
        }
    }
}
