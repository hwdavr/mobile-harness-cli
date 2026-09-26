package com.example.mobileharness.annotations

/** Marks a public ViewModel method that the Android mobile harness may invoke. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
public annotation class MobileAction(
    val name: String,
    val description: String = "",
    val parameters: Array<MobileActionParameter> = [],
    /** Optional state predicate: `property=value1|value2`; otherwise await the next state change. */
    val awaitForState: String = "",
    val awaitForStateChange: Boolean = false,
)

/** Describes one JSON input mapped to a ViewModel action parameter. */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class MobileActionParameter(
    val name: String,
    val type: String,
    val required: Boolean = true,
    val defaultValue: String = "",
)
