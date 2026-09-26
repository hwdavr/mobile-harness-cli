package com.example.mobileharness.annotations

/**
 * Marks a feature or Android ViewModel as available to the generated mobile harness.
 *
 * Pure JVM features may:
 * - implement Feature<State, Action, Effect> directly;
 * - have a public no-arg constructor;
 * - use @Serializable state/action/effect types;
 * - use a sealed action hierarchy with @SerialName for stable CLI action names.
 *
 * Android ViewModels are collected into a generated registry for instrumentation harnesses.
 * `permissions` and `requiredSystemFeatures` are checked when invoking actions. The optional
 * cleanup action runs only when `cleanupWhenState` (`property=value1|value2`) matches the final
 * state, so saved or otherwise completed work is not discarded.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class MobileFeature(
    val name: String,
    val description: String = "",
    val permissions: Array<String> = [],
    val requiredSystemFeatures: Array<String> = [],
    val cleanupAction: String = "",
    val cleanupWhenState: String = "",
)
