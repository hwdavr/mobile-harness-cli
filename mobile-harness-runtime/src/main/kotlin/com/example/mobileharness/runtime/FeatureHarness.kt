package com.example.mobileharness.runtime

import kotlinx.serialization.json.JsonElement

public interface FeatureHarness {
    public val descriptor: FeatureDescriptor

    /**
     * In the zero-boilerplate path, a fixture is simply serialized State JSON.
     * More complex dependency/fake fixtures can be added later through a factory extension.
     */
    public fun initialState(fixture: JsonElement? = null): JsonElement

    public fun execute(
        state: JsonElement,
        action: String,
        input: JsonElement,
    ): HarnessOutput
}

public interface FeatureProvider {
    public fun create(): FeatureHarness
}
