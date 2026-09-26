package com.example.mobileharness.runtime

import kotlinx.serialization.json.Json

public object HarnessJson {
    public val default: Json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        prettyPrint = false
    }
}
