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
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class MobileFeature(
    val name: String,
    val description: String = "",
)
