package org.mule.extension.circuitbreaker.internal;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerState {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerState.class);
    private static final ConcurrentHashMap<String, CircuitBreakerState> REGISTRY = new ConcurrentHashMap<>();

    public static CircuitBreakerState get(String name) {
        return REGISTRY.computeIfAbsent(name, k -> new CircuitBreakerState());
    }
    
    // For testing purposes
    public static void clearRegistry() {
        REGISTRY.clear();
    }

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final AtomicReference<State> currentState = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public State getCurrentState() {
        return currentState.get();
    }

    public void recordFailure(CircuitBreakerOperations.CircuitBreakerConfig config) {
        lastFailureTime.set(System.currentTimeMillis());
        
        if (currentState.get() == State.HALF_OPEN) {
            LOGGER.info("Failure in HALF_OPEN state. Re-opening circuit.");
            openCircuit();
            return;
        }

        if (currentState.get() == State.CLOSED) {
            int currentFailures = failureCount.incrementAndGet();
            LOGGER.debug("Recorded failure. Count: {}/{}", currentFailures, config.getFailureThreshold());
            if (currentFailures >= config.getFailureThreshold()) {
                LOGGER.info("Failure threshold reached. Opening circuit.");
                openCircuit();
            }
        }
    }

    public void recordSuccess(CircuitBreakerOperations.CircuitBreakerConfig config) {
        if (currentState.get() == State.HALF_OPEN) {
            int currentSuccesses = successCount.incrementAndGet();
            LOGGER.debug("Recorded success in HALF_OPEN. Count: {}/{}", currentSuccesses, config.getHalfOpenAttempts());
            if (currentSuccesses >= config.getHalfOpenAttempts()) {
                LOGGER.info("Half-open attempts successful. Closing circuit.");
                closeCircuit();
            }
        } else if (currentState.get() == State.CLOSED) {
            // Optional: Reset failure count on success? 
            // Standard CB pattern often resets failure count on success in CLOSED state to handle "consecutive" failures.
            // If we want "consecutive" failures, we reset here.
            if (failureCount.get() > 0) {
                LOGGER.debug("Success in CLOSED state. Resetting failure count.");
                failureCount.set(0);
            }
        }
    }

    public void checkState(CircuitBreakerOperations.CircuitBreakerConfig config) {
        if (currentState.get() == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime.get();
            if (elapsed >= config.getTimeoutMillis()) {
                if (currentState.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    successCount.set(0);
                    LOGGER.info("Timeout elapsed. Circuit transitioning to HALF_OPEN. Testing downstream...");
                }
            }
        }
    }

    private void openCircuit() {
        currentState.set(State.OPEN);
        failureCount.set(0);
        successCount.set(0);
    }

    private void closeCircuit() {
        currentState.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
    }
    
    // For testing
    public void reset() {
        closeCircuit();
    }
}
