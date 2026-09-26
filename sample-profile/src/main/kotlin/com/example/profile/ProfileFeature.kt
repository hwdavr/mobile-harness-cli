package com.example.profile

import com.example.mobileharness.annotations.MobileFeature
import com.example.mobileharness.runtime.AnalyticsEffect
import com.example.mobileharness.runtime.Feature
import com.example.mobileharness.runtime.NavigationEffect
import com.example.mobileharness.runtime.Transition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ProfileState(
    val userId: String = "USER_001",
    val phone: String = "91234567",
    val verificationRequired: Boolean = false,
)

@Serializable
public sealed interface ProfileAction {
    @Serializable
    @SerialName("updatePhone")
    public data class UpdatePhone(
        val phone: String,
    ) : ProfileAction

    @Serializable
    @SerialName("confirmPhoneUpdate")
    public data object ConfirmPhoneUpdate : ProfileAction
}

@Serializable
public sealed interface ProfileEffect {
    @Serializable
    @SerialName("navigation")
    public data class Navigate(
        override val destination: String,
    ) : ProfileEffect, NavigationEffect

    @Serializable
    @SerialName("analytics")
    public data class Analytics(
        override val event: String,
        override val properties: Map<String, String> = emptyMap(),
    ) : ProfileEffect, AnalyticsEffect
}

@MobileFeature(
    name = "profile",
    description = "Profile business behavior",
)
public class ProfileFeature : Feature<ProfileState, ProfileAction, ProfileEffect> {
    override fun initialState(): ProfileState = ProfileState()

    override fun reduce(
        state: ProfileState,
        action: ProfileAction,
    ): Transition<ProfileState, ProfileEffect> = when (action) {
        is ProfileAction.UpdatePhone -> Transition(
            state = state.copy(
                phone = action.phone,
                verificationRequired = true,
            ),
            effects = listOf(
                ProfileEffect.Navigate(destination = "otp"),
                ProfileEffect.Analytics(event = "phone_update_started"),
            ),
        )

        ProfileAction.ConfirmPhoneUpdate -> Transition(
            state = state.copy(verificationRequired = false),
        )
    }
}
