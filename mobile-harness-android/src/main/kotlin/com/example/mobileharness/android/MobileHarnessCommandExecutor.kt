package com.example.mobileharness.android

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mobileharness.annotations.MobileAction
import com.example.mobileharness.annotations.MobileFeature
import com.squareup.moshi.Moshi
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/** Executes the mobile-harness JSON protocol against annotated ViewModels on the target device. */
public class MobileHarnessCommandExecutor<A : ComponentActivity>(
    private val viewModels: List<Class<*>>,
    private val activityClass: Class<A>,
    private val moshi: Moshi,
) {
    public fun executeFromInstrumentation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val command = arguments.getString(ARG_COMMAND).orEmpty()
        val featureName = arguments.getString(ARG_FEATURE).orEmpty()
        val result = when (command) {
            "list" -> listFeatures()
            "describe" -> describeFeature(featureName)
            "action" -> executeAction(
                featureName = featureName,
                actionName = arguments.getString(ARG_ACTION).orEmpty(),
                inputJson = decodeInput(arguments.getString(ARG_INPUT_BASE64).orEmpty()),
                await = arguments.awaitCondition(),
            )
            else -> error("Unsupported mobile harness command '$command'")
        }
        println("$RESULT_MARKER$result")
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString(RESULT_BUNDLE_KEY, result.toString()) },
        )
    }

    private fun listFeatures(): JSONArray = JSONArray().apply {
        viewModels
            .sortedBy { it.getAnnotation(MobileFeature::class.java)?.name.orEmpty() }
            .forEach { put(descriptor(it)) }
    }

    private fun describeFeature(featureName: String): JSONObject =
        descriptor(viewModelClass(featureName))

    private fun viewModelClass(featureName: String): Class<*> = viewModels.singleOrNull {
        it.getAnnotation(MobileFeature::class.java)?.name == featureName
    } ?: error("Unknown app feature '$featureName'")

    private fun descriptor(viewModelClass: Class<*>): JSONObject {
        val feature = requireNotNull(viewModelClass.getAnnotation(MobileFeature::class.java)) {
            "${viewModelClass.simpleName} is missing @MobileFeature"
        }
        val actions = JSONArray()
        viewModelClass.declaredMethods.mapNotNull { method ->
            method.getAnnotation(MobileAction::class.java)?.let { method to it }
        }.sortedBy { (_, annotation) -> annotation.name }.forEach { (method, annotation) ->
            require(method.parameterCount == annotation.parameters.size) {
                "@MobileAction '${annotation.name}' parameter metadata does not match its method"
            }
            val parameters = JSONArray()
            annotation.parameters.forEach { parameter ->
                parameters.put(
                    JSONObject()
                        .put("name", parameter.name)
                        .put("type", parameter.type)
                        .put("required", parameter.required)
                        .put("defaultValue", parameter.defaultValue),
                )
            }
            actions.put(
                JSONObject()
                    .put("name", annotation.name)
                    .put("description", annotation.description)
                    .put("awaitForState", annotation.awaitForState)
                    .put("awaitForStateChange", annotation.awaitForStateChange)
                    .put("parameters", parameters),
            )
        }
        return JSONObject()
            .put("name", feature.name)
            .put("description", feature.description)
            .put("actions", actions)
    }

    private fun executeAction(
        featureName: String,
        actionName: String,
        inputJson: String,
        await: JSONObject?,
    ): JSONObject {
        val viewModelClass = viewModelClass(featureName).asSubclass(ViewModel::class.java)
        val feature = requireNotNull(viewModelClass.getAnnotation(MobileFeature::class.java))
        verifyFeatureRequirements(feature)
        val harness = MobileViewModelHarness(
            viewModelClass = viewModelClass,
            host = ActivityScenarioViewModelHost(activityClass),
            moshi = moshi,
        )

        try {
            val input = JSONObject(inputJson)
            val setupActions = input.optJSONArray(SETUP_ACTIONS_KEY) ?: JSONArray()
            for (index in 0 until setupActions.length()) {
                val setup = setupActions.getJSONObject(index)
                harness.invokeActionAndAwait(
                    actionName = setup.getString("action"),
                    input = setup.optJSONObject("input") ?: JSONObject(),
                    await = setup.optJSONObject(AWAIT_KEY),
                )
            }
            input.remove(SETUP_ACTIONS_KEY)
            val actionAwait = input.optJSONObject(AWAIT_KEY) ?: await
            input.remove(AWAIT_KEY)
            val uiState = harness.invokeActionAndAwait(actionName, input, actionAwait)
            val stateJson = moshi.adapter<Any>(uiState.javaClass).toJson(uiState)
            return JSONObject()
                .put("protocolVersion", 1)
                .put("state", JSONObject(stateJson))
                .put("effects", JSONArray())
                .put("analytics", JSONArray())
                .put("errors", JSONArray())
        } finally {
            try {
                val cleanupAction = feature.cleanupAction
                if (cleanupAction.isNotBlank() && cleanupAction != actionName) {
                    val currentState = harness.stateSnapshot()
                    if (
                        feature.cleanupWhenState.isBlank() ||
                        harness.matchesStatePredicate(currentState, feature.cleanupWhenState)
                    ) {
                        harness.invokeActionAndAwait(
                            actionName = cleanupAction,
                            input = JSONObject(),
                        )
                    }
                }
            } finally {
                harness.close()
            }
        }
    }

    private fun verifyFeatureRequirements(feature: MobileFeature) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        feature.requiredSystemFeatures.forEach { systemFeature ->
            check(context.packageManager.hasSystemFeature(systemFeature)) {
                "The target device lacks required system feature '$systemFeature'."
            }
        }
        feature.permissions.forEach { permission ->
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
            check(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                "Required permission '$permission' was not granted."
            }
        }
    }

    private fun decodeInput(encoded: String): String =
        String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)

    private fun Bundle.awaitCondition(): JSONObject? {
        val path = getString(ARG_AWAIT_PATH) ?: return null
        require(containsKey(ARG_AWAIT_EQUALS)) {
            "'$ARG_AWAIT_PATH' requires '$ARG_AWAIT_EQUALS'"
        }
        return JSONObject()
            .put("path", path)
            .put("equals", getString(ARG_AWAIT_EQUALS))
    }

    private companion object {
        const val ARG_ACTION = "mobileHarnessAction"
        const val ARG_AWAIT_EQUALS = "mobileHarnessAwaitEquals"
        const val ARG_AWAIT_PATH = "mobileHarnessAwaitPath"
        const val ARG_COMMAND = "mobileHarnessCommand"
        const val ARG_FEATURE = "mobileHarnessFeature"
        const val ARG_INPUT_BASE64 = "mobileHarnessInputBase64"
        const val AWAIT_KEY = "_mobileHarnessAwait"
        const val RESULT_BUNDLE_KEY = "mobileHarnessResult"
        const val RESULT_MARKER = "MOBILE_HARNESS_RESULT:"
        const val SETUP_ACTIONS_KEY = "before"
    }
}
