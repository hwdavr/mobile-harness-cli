package com.example.mobileharness.runtime

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Shared implementation used by KSP-generated feature adapters.
 */
public abstract class GeneratedFeatureHarness<S : Any, A : Any, E : Any>(
    final override val descriptor: FeatureDescriptor,
    private val stateSerializer: KSerializer<S>,
    private val actionSerializer: KSerializer<A>,
    private val effectSerializer: KSerializer<E>,
) : FeatureHarness {

    protected abstract fun createFeature(): Feature<S, A, E>

    final override fun initialState(fixture: JsonElement?): JsonElement {
        val json = HarnessJson.default
        if (fixture != null) {
            // Decode + re-encode so invalid fixtures fail early and output is canonical.
            val typed = json.decodeFromJsonElement(stateSerializer, fixture)
            return json.encodeToJsonElement(stateSerializer, typed)
        }

        val feature = createFeature()
        return json.encodeToJsonElement(stateSerializer, feature.initialState())
    }

    final override fun execute(
        state: JsonElement,
        action: String,
        input: JsonElement,
    ): HarnessOutput {
        val json = HarnessJson.default

        return try {
            val typedState = json.decodeFromJsonElement(stateSerializer, state)
            val inputObject = input as? JsonObject
                ?: error("Action input must be a JSON object")

            val actionJson = buildJsonObject {
                put("type", JsonPrimitive(action))
                inputObject.forEach { (key, value) ->
                    if (key != "type") put(key, value)
                }
            }

            val typedAction = json.decodeFromJsonElement(actionSerializer, actionJson)
            val result = createFeature().reduce(typedState, typedAction)

            val normalEffects = mutableListOf<JsonElement>()
            val analytics = mutableListOf<AnalyticsEvent>()

            result.effects.forEach { effect ->
                if (effect is AnalyticsEffect) {
                    analytics += AnalyticsEvent(
                        event = effect.event,
                        properties = effect.properties,
                    )
                } else {
                    normalEffects += json.encodeToJsonElement(effectSerializer, effect)
                }
            }

            HarnessOutput(
                state = json.encodeToJsonElement(stateSerializer, result.state),
                effects = normalEffects,
                analytics = analytics,
            )
        } catch (t: Throwable) {
            HarnessOutput(
                state = state,
                errors = listOf(
                    HarnessError(
                        code = "FEATURE_ERROR",
                        message = t.message ?: t::class.simpleName ?: "Unknown error",
                    ),
                ),
            )
        }
    }
}
