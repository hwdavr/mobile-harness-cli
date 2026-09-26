package com.example.mobileharness.android

import androidx.lifecycle.ViewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileharness.annotations.MobileAction
import com.example.mobileharness.annotations.MobileFeature
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

public class MobileViewModelHarness internal constructor(
    private val viewModelClass: Class<out ViewModel>,
    private val host: MobileHarnessViewModelHost,
    private val moshi: Moshi,
) {
    public fun invokeActionAndAwait(
        actionName: String,
        input: JSONObject,
        await: JSONObject? = null,
    ): Any {
        val previousState = stateSnapshot()
        val action = actionMetadata(actionName)
        host.withViewModel(viewModelClass) { viewModel ->
            val method = viewModelClass.declaredMethods.single { candidate ->
                candidate.getAnnotation(MobileAction::class.java)?.name == actionName
            }
            val annotation = requireNotNull(method.getAnnotation(MobileAction::class.java))
            require(method.parameterCount == annotation.parameters.size) {
                "@MobileAction '$actionName' declares ${annotation.parameters.size} inputs for " +
                    "${method.parameterCount} method parameters"
            }
            val arguments = annotation.parameters.mapIndexed { index, parameter ->
                val rawValue = when {
                    input.has(parameter.name) -> input.get(parameter.name)
                    parameter.required -> error("Missing required action input '${parameter.name}'")
                    else -> parameter.defaultValue
                }
                convertArgument(rawValue, method.parameterTypes[index])
            }.toTypedArray()
            method.isAccessible = true
            method.invoke(viewModel, *arguments)
            Unit
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        if (await?.optBoolean("unchanged") == true) return stateSnapshot()

        val statePredicate = await?.let(::predicateFromRequest)
            ?: action.awaitForState.takeIf(String::isNotBlank)?.let(::StatePredicate)
        val requireChange = when {
            await != null -> true
            statePredicate != null -> action.awaitForStateChange
            else -> true
        }
        return awaitUiState { state ->
            (!requireChange || state != previousState) &&
                (statePredicate?.matches(state) ?: (state != previousState))
        }
    }

    public fun stateSnapshot(): Any = host.withViewModel(viewModelClass) { viewModel ->
        requireNotNull(uiStateFlow(viewModel).value) { "ViewModel uiState was null" }
    }

    public fun featureAnnotation(): MobileFeature = requireNotNull(
        viewModelClass.getAnnotation(MobileFeature::class.java),
    ) { "${viewModelClass.name} is missing @MobileFeature" }

    public fun matchesStatePredicate(state: Any, expression: String): Boolean =
        StatePredicate(expression).matches(state)

    public fun actionMetadata(actionName: String): MobileAction = viewModelClass.declaredMethods
        .mapNotNull { method -> method.getAnnotation(MobileAction::class.java) }
        .singleOrNull { annotation -> annotation.name == actionName }
        ?: error("No @MobileAction named '$actionName' on ${viewModelClass.simpleName}")

    public fun awaitUiState(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        condition: (Any) -> Boolean,
    ): Any {
        val flow = host.withViewModel(viewModelClass) { viewModel -> uiStateFlow(viewModel) }
        return requireNotNull(
            runBlocking {
                withTimeout(timeoutMs) {
                    flow.first { state -> state != null && condition(state) }
                }
            },
        ) { "ViewModel uiState was null" }
    }

    public fun close() {
        host.close()
    }

    private fun convertArgument(value: Any, targetType: Class<*>): Any? {
        if (value == JSONObject.NULL || value == "null") {
            require(!targetType.isPrimitive) { "Cannot pass null to ${targetType.simpleName}" }
            return null
        }
        return when {
            targetType == String::class.java -> value.toString()
            targetType == Boolean::class.javaPrimitiveType || targetType == Boolean::class.javaObjectType ->
                value.toString().toBooleanStrict()
            targetType == Int::class.javaPrimitiveType || targetType == Int::class.javaObjectType ->
                value.toString().toInt()
            targetType == Long::class.javaPrimitiveType || targetType == Long::class.javaObjectType ->
                value.toString().toLong()
            targetType == Float::class.javaPrimitiveType || targetType == Float::class.javaObjectType ->
                value.toString().toFloat()
            targetType.isEnum -> requireNotNull(targetType.enumConstants)
                .filterIsInstance<Enum<*>>()
                .firstOrNull { it.name == value.toString() }
                ?: error("Unknown ${targetType.simpleName} value '$value'")
            else -> error("Unsupported @MobileAction parameter type: ${targetType.name}")
        }
    }

    private fun uiStateFlow(viewModel: ViewModel): StateFlow<*> =
        viewModel.javaClass.getMethod("getUiState").invoke(viewModel) as? StateFlow<*>
            ?: error("${viewModel.javaClass.name}.uiState must be a StateFlow")

    private fun predicateFromRequest(request: JSONObject): StatePredicate? {
        if (!request.has("path")) return null
        require(request.has("equals")) { "Await conditions require an 'equals' value" }
        return StatePredicate("${request.getString("path")}=${request.get("equals")}")
    }

    private inner class StatePredicate(
        expression: String,
    ) {
        private val path: List<String>
        private val expectedValues: Set<String>

        init {
            val separator = expression.indexOf('=')
            require(separator > 0) {
                "State predicate must use 'property=value1|value2', got '$expression'"
            }
            path = expression.substring(0, separator).split('.').also { parts ->
                require(parts.none(String::isBlank)) { "State predicate path cannot contain empty segments" }
            }
            expectedValues = expression.substring(separator + 1).split('|').toSet()
            require(expectedValues.none(String::isBlank)) { "State predicate values cannot be blank" }
        }

        fun matches(state: Any): Boolean {
            var jsonValue: Any? = JSONObject(moshi.adapter<Any>(state.javaClass).toJson(state))
            path.forEach { segment ->
                jsonValue = when (val current = jsonValue) {
                    is JSONObject -> current.opt(segment)
                    is JSONArray -> segment.toIntOrNull()?.let(current::opt)
                    else -> null
                }
            }
            val actualValue = if (jsonValue == JSONObject.NULL) "null" else jsonValue?.toString() ?: "null"
            return actualValue in expectedValues
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
