package com.example.mobileharness.android

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import java.io.Closeable

public interface MobileHarnessViewModelHost : Closeable {
    public fun <R : Any> withViewModel(
        viewModelClass: Class<out ViewModel>,
        action: (ViewModel) -> R,
    ): R
}

/** Launches a lifecycle owner and resolves the requested ViewModel through its installed factory. */
public class ActivityScenarioViewModelHost<A : ComponentActivity>(
    activityClass: Class<A>,
) : MobileHarnessViewModelHost {
    private val scenario: ActivityScenario<A> = ActivityScenario.launch(activityClass)

    override fun <R : Any> withViewModel(
        viewModelClass: Class<out ViewModel>,
        action: (ViewModel) -> R,
    ): R {
        lateinit var result: R
        scenario.onActivity { activity ->
            result = action(ViewModelProvider(activity)[viewModelClass])
        }
        return result
    }

    override fun close() {
        scenario.close()
    }
}
