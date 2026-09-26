package com.example.mobileharness.runtime

import java.util.ServiceLoader

public class FeatureRegistry private constructor(
    private val features: Map<String, FeatureHarness>,
) {
    public fun list(): List<FeatureDescriptor> =
        features.values.map { it.descriptor }.sortedBy { it.name }

    public fun get(name: String): FeatureHarness =
        features[name] ?: error("Unknown feature: $name")

    public companion object {
        /**
         * Discovers KSP-generated FeatureProvider implementations from all jars/modules
         * on the JVM classpath. This is Java ServiceLoader, not reflection/classpath scanning.
         */
        public fun discover(
            classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
        ): FeatureRegistry {
            val providers = ServiceLoader.load(FeatureProvider::class.java, classLoader).toList()
            val harnesses = providers.map { it.create() }

            val duplicates = harnesses
                .groupBy { it.descriptor.name }
                .filterValues { it.size > 1 }
                .keys

            require(duplicates.isEmpty()) {
                "Duplicate mobile feature names: ${duplicates.sorted().joinToString()}"
            }

            return FeatureRegistry(harnesses.associateBy { it.descriptor.name })
        }
    }
}
