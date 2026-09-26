# Mobile Harness KSP Starter

A compile-time annotation/code-generation library for JVM feature harnesses and Android ViewModel instrumentation.

## Modules

- `mobile-harness-annotations` — `@MobileFeature` and `@MobileAction` annotations. Keep this dependency tiny.
- `mobile-harness-runtime` — `Feature`, `Transition`, harness protocol, JSON execution, semantic effect markers, and `ServiceLoader` discovery.
- `mobile-harness-ksp` — KSP processor. Generates typed JVM feature adapters/providers and an Android ViewModel registry.
- `mobile-harness-android` — Android instrumentation command executor, Hilt-compatible ViewModel host, action invocation, state waiting, and protocol serialization.
- `sample-profile` — example feature showing the intended developer experience.
- `sample-cli` — tiny demo CLI proving feature discovery without a manual registry.

## What feature developers write

```kotlin
@Serializable
data class ProfileState(...)

@Serializable
sealed interface ProfileAction {
    @Serializable
    @SerialName("updatePhone")
    data class UpdatePhone(val phone: String) : ProfileAction
}

@Serializable
sealed interface ProfileEffect { ... }

@MobileFeature("profile")
class ProfileFeature : Feature<ProfileState, ProfileAction, ProfileEffect> {
    override fun initialState() = ProfileState(...)
    override fun reduce(state: ProfileState, action: ProfileAction) = ...
}
```

No handwritten `ProfileHarness`, action decoder, feature descriptor, or registry entry is required.

## Generated code

For `ProfileFeature`, KSP generates approximately:

- `ProfileFeature_MobileHarness`
- `ProfileFeature_MobileProvider`
- `META-INF/services/com.example.mobileharness.runtime.FeatureProvider`

The CLI calls:

```kotlin
val registry = FeatureRegistry.discover()
```

Java `ServiceLoader` finds all generated providers on the CLI classpath. This avoids runtime classpath scanning/reflection.

## Consumer Gradle setup

In each pure JVM feature-core module:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":mobile-harness-annotations"))
    implementation(project(":mobile-harness-runtime"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    ksp(project(":mobile-harness-ksp"))
}
```

Your `mobile-cli` module only needs the runtime plus the feature modules it wants on its classpath:

```kotlin
dependencies {
    implementation(project(":mobile-harness-runtime"))
    implementation(project(":features:profile:core"))
    implementation(project(":features:billing:core"))
}
```

There is no manual feature registry. Gradle still needs to know which modules belong to the CLI distribution; that is a build dependency problem, not a runtime registry problem.

### Android ViewModel discovery

Annotate each app ViewModel with `@MobileFeature` and its callable methods with `@MobileAction`.
KSP generates `com.example.mobileharness.generated.MobileViewModelRegistry` from all annotated
ViewModels. `mobile-harness-android` provides the generic instrumentation command executor and
resolves each class through the supplied activity's `ViewModelProvider`, so the app test only
provides its generated registry, Hilt activity, and state serializer. Registered ViewModels expose
a `uiState: StateFlow` for snapshots. Adding another ViewModel requires no registry, factory, or
action-dispatch branch in the app instrumentation test.

`@MobileAction(awaitForState = "status=Recording|Error", awaitForStateChange = true)` can describe
an asynchronous completion predicate. Predicates use a serialized UI-state path and accepted value
list; without one, the executor waits for the next state change. The CLI can override this per call
with `_mobileHarnessAwait: {"path":"status","equals":"Recording"}`. Feature annotations can
declare device permissions/system features and a conditional cleanup action, keeping those rules
out of the shared command runner.

For a local composite build, include this project from the Android app's settings:

```kotlin
includeBuild("mobile-harness-cli")
```

Then add to the Android app module:

```kotlin
dependencies {
    implementation("com.example.mobileharness:mobile-harness-annotations:0.1.0")
    ksp("com.example.mobileharness:mobile-harness-ksp:0.1.0")
    androidTestImplementation("com.example.mobileharness:mobile-harness-android:0.1.0")
}
```

## Run the sample

```bash
./gradlew :sample-profile:test
./gradlew :sample-cli:run --args="list"
./gradlew :sample-cli:run --args="describe profile"
./gradlew :sample-cli:run --args='action profile updatePhone {"phone":"98765432"}'
```

Later:

```bash
./gradlew :sample-cli:installDist
./sample-cli/build/install/mobile-cli/bin/mobile-cli list
```

## V1 constraints

The zero-boilerplate generated path intentionally has a few constraints:

1. `@MobileFeature` must directly implement `Feature<State, Action, Effect>`.
2. The feature must be an `object`, have a public no-arg constructor, or have only defaulted constructor parameters.
3. State, Action, and Effect must use `@Serializable`.
4. Actions should be nested subclasses of a sealed action hierarchy.
5. Give each action a stable `@SerialName`, because that becomes the CLI action name.
6. A fixture is state-shaped JSON in V1. Dependency/API/clock fake configuration should be added later via a `FeatureFactory` extension instead of reflection-based constructor injection.

These constraints are deliberate: they keep the generated code deterministic and compile-time checked.

## Version notes (September 2026)

This library currently targets the Kotlin/KSP line used by its Android consumer and pins:

- Kotlin `2.0.0`
- kotlinx.serialization `1.7.3`
- KSP `2.0.0-1.0.22`
- KotlinPoet `1.18.1`

Keep KSP aligned with the consuming Android Gradle Plugin and Kotlin versions. This repository
uses AGP 8.5.2 and Kotlin 2.0.0, so the KSP plugin and processor API are aligned to that toolchain.

## Recommended next extension

Do not add general reflection/DI next. Add an explicit `FeatureFactory` extension for dependency-heavy features:

```text
fixture -> HarnessEnvironment -> FakeRepository/FakeClock -> Feature
```

That preserves deterministic fixtures while keeping production Android dependencies outside the JVM harness.
