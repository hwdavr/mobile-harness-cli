package com.example.profile

import com.example.mobileharness.runtime.FeatureRegistry
import com.example.mobileharness.runtime.HarnessJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

public class ProfileHarnessIntegrationTest {
    @Test
    public fun `generated profile harness is discoverable and executable`() {
        val registry = FeatureRegistry.discover()
        val harness = registry.get("profile")
        val initial = harness.initialState()

        val output = harness.execute(
            state = initial,
            action = "updatePhone",
            input = buildJsonObject {
                put("phone", "98765432")
            },
        )

        assertTrue(output.errors.isEmpty())
        assertEquals(
            "98765432",
            output.state.jsonObject.getValue("phone").jsonPrimitive.content,
        )
        assertEquals("phone_update_started", output.analytics.single().event)
        assertEquals(
            "otp",
            output.effects.single().jsonObject.getValue("destination").jsonPrimitive.content,
        )
    }
}
