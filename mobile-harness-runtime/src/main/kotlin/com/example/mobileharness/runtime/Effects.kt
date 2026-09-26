package com.example.mobileharness.runtime

/** Marker for an effect that should be lifted into the top-level analytics array. */
public interface AnalyticsEffect {
    public val event: String
    public val properties: Map<String, String>
}

/** Optional semantic marker. Navigation effects still remain in `effects`. */
public interface NavigationEffect {
    public val destination: String
}

/** Optional semantic marker. Dialog effects still remain in `effects`. */
public interface DialogEffect {
    public val dialogId: String
}
