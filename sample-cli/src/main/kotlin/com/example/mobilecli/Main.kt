package com.example.mobilecli

import com.example.mobileharness.runtime.FeatureRegistry
import com.example.mobileharness.runtime.HarnessJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public fun main(args: Array<String>) {
    val registry = FeatureRegistry.discover()
    val json = HarnessJson.default

    when (args.firstOrNull()) {
        "list" -> println(json.encodeToString(registry.list()))

        "describe" -> {
            val feature = requireNotNull(args.getOrNull(1)) { "feature name required" }
            println(json.encodeToString(registry.get(feature).descriptor))
        }

        "action" -> {
            val featureName = requireNotNull(args.getOrNull(1)) { "feature name required" }
            val actionName = requireNotNull(args.getOrNull(2)) { "action name required" }
            val inputText = args.getOrNull(3) ?: "{}"
            val harness = registry.get(featureName)
            val output = harness.execute(
                state = harness.initialState(),
                action = actionName,
                input = json.parseToJsonElement(inputText),
            )
            println(json.encodeToString(output))
        }

        else -> {
            println("Usage:")
            println("  mobile-cli list")
            println("  mobile-cli describe <feature>")
            println("  mobile-cli action <feature> <action> '<json>'")
        }
    }
}
