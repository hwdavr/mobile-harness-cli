package com.example.mobileharness.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class ParameterDescriptor(
    val name: String,
    val type: String,
    val required: Boolean = true,
)

@Serializable
public data class ActionDescriptor(
    val name: String,
    val parameters: List<ParameterDescriptor> = emptyList(),
)

@Serializable
public data class FeatureDescriptor(
    val name: String,
    val description: String = "",
    val actions: List<ActionDescriptor> = emptyList(),
)

@Serializable
public data class AnalyticsEvent(
    val event: String,
    val properties: Map<String, String> = emptyMap(),
)

@Serializable
public data class HarnessError(
    val code: String,
    val message: String,
)

@Serializable
public data class HarnessOutput(
    val protocolVersion: Int = 1,
    val state: JsonElement,
    val effects: List<JsonElement> = emptyList(),
    val analytics: List<AnalyticsEvent> = emptyList(),
    val errors: List<HarnessError> = emptyList(),
)
