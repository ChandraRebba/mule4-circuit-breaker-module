package org.mule.extension.circuitbreaker.internal;

import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import org.mule.sdk.api.meta.JavaVersion;

@Xml(prefix = "circuit-breaker")
@Extension(name = "Circuit Breaker")
@Configurations(CircuitBreakerConfiguration.class)
@JavaVersionSupport({JavaVersion.JAVA_17})
public class CircuitBreakerExtension {
}
