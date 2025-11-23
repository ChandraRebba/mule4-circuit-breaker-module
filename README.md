# Mule 4 Circuit Breaker Extension

A custom Mule 4 module that implements the Circuit Breaker pattern to protect downstream services from cascading failures.

## Features

- **Configurable Failure Threshold**: Set the number of failures before the circuit opens.
- **Timeout Duration**: Define how long the circuit stays open before attempting to recover (Half-Open).
- **Half-Open Attempts**: Specify the number of successful requests required to close the circuit.
- **Error Filtering**:
    - **Included Errors**: Specify exactly which errors should trip the circuit (e.g., `HTTP:INTERNAL_SERVER_ERROR`, `HTTP:SERVICE_UNAVAILABLE`).
    - **Optimistic Matching**: Supports matching by full `NAMESPACE:IDENTIFIER` or just `IDENTIFIER` if the namespace cannot be resolved from the exception.
- **Shared State**: Share circuit state across multiple flows using a unique `circuitName`.

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>{YOUR-ORG-ID}</groupId>
    <artifactId>circuit-breaker-module</artifactId>
    <version>1.0.0</version>
    <classifier>mule-plugin</classifier>
</dependency>
```

## Usage

Wrap any operation (like an HTTP Request) inside the `circuit-breaker:filter` scope.

```xml
<circuit-breaker:filter doc:name="Circuit Breaker" 
                        failureThreshold="5" 
                        timeoutMinutes="2" 
                        halfOpenAttempts="3" 
                        includedErrors="HTTP:NOT_FOUND,HTTP:INTERNAL_SERVER_ERROR"
                        circuitName="my-api-circuit">
    <http:request method="GET" config-ref="HTTP_Request_configuration" path="/api/resource"/>
</circuit-breaker:filter>
```

### Configuration Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| **Failure Threshold** | Number of consecutive failures required to open the circuit. | 5 |
| **Timeout (minutes)** | Time to wait in OPEN state before transitioning to HALF-OPEN. | 5 |
| **Half-Open Attempts** | Number of successful requests required in HALF-OPEN state to close the circuit. | 3 |
| **Included Errors** | Comma-separated list of error types to track. If empty, ALL errors are tracked. | (Empty) |
| **Circuit Name** | Unique identifier for the circuit. Operations with the same name share the same state. | `default-circuit` |

## Error Handling Logic

The module uses a robust error matching strategy:

1.  **Full Type Match**: Checks if the thrown error matches `NAMESPACE:IDENTIFIER` (e.g., `HTTP:NOT_FOUND`).
2.  **Identifier Match**: If the namespace cannot be extracted (common with some internal Mule errors), it falls back to checking just the identifier (e.g., `NOT_FOUND`).
3.  **Exception Class Match**: Checks if the Java exception class name matches.

## Debugging

To see detailed logs about error matching and state transitions, enable DEBUG logging for the extension in your `log4j2.xml`:

```xml
<AsyncLogger name="org.mule.extension.circuitbreaker" level="DEBUG"/>
```
