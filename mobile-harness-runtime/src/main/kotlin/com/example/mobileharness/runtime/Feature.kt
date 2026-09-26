package com.example.mobileharness.runtime

public interface Feature<S : Any, A : Any, E : Any> {
    public fun initialState(): S

    public fun reduce(
        state: S,
        action: A,
    ): Transition<S, E>
}

public data class Transition<S : Any, E : Any>(
    val state: S,
    val effects: List<E> = emptyList(),
)
