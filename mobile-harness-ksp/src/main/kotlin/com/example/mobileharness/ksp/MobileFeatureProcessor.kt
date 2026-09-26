package com.example.mobileharness.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier as KSModifier
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.OutputStreamWriter

private const val MOBILE_FEATURE = "com.example.mobileharness.annotations.MobileFeature"
private const val ANDROID_VIEW_MODEL = "androidx.lifecycle.ViewModel"
private const val FEATURE = "com.example.mobileharness.runtime.Feature"
private const val SERIAL_NAME = "kotlinx.serialization.SerialName"
private const val FEATURE_PROVIDER = "com.example.mobileharness.runtime.FeatureProvider"
private const val GENERATED_PACKAGE = "com.example.mobileharness.generated"
private const val VIEW_MODEL_REGISTRY = "MobileViewModelRegistry"

public class MobileFeatureProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private val generatedProviderNames = linkedSetOf<String>()
    private val originatingFiles = linkedSetOf<com.google.devtools.ksp.symbol.KSFile>()
    private val processedFeatures = linkedSetOf<String>()
    private val viewModels = sortedMapOf<String, KSClassDeclaration>()
    private val viewModelFiles = linkedSetOf<com.google.devtools.ksp.symbol.KSFile>()
    private var servicesWritten = false
    private var viewModelRegistryWritten = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation(MOBILE_FEATURE)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        val deferredSymbols = mutableListOf<KSAnnotated>()

        symbols.forEach { feature ->
            if (!feature.validate()) {
                deferredSymbols += feature
                return@forEach
            }
            val qualifiedName = feature.qualifiedName?.asString() ?: return@forEach
            if (!processedFeatures.add(qualifiedName)) return@forEach
            if (feature.isAndroidViewModel()) {
                if (feature.modifiers.contains(KSModifier.ABSTRACT)) {
                    logger.error("@MobileFeature Android ViewModels must be concrete", feature)
                } else {
                    viewModels[qualifiedName] = feature
                    feature.containingFile?.let(viewModelFiles::add)
                }
            } else {
                generateForFeature(feature)
            }
            feature.containingFile?.let(originatingFiles::add)
        }

        return deferredSymbols
    }

    override fun finish() {
        if (!viewModelRegistryWritten && viewModels.isNotEmpty()) {
            generateViewModelRegistry()
            viewModelRegistryWritten = true
        }
        if (!servicesWritten && generatedProviderNames.isNotEmpty()) {
            writeServiceLoaderFile()
            servicesWritten = true
        }
    }

    private fun KSClassDeclaration.isAndroidViewModel(
        visited: MutableSet<String> = mutableSetOf(),
    ): Boolean {
        val declarationName = qualifiedName?.asString() ?: return false
        if (!visited.add(declarationName)) return false

        return superTypes.any { superType ->
            val superDeclaration = superType.resolve().declaration
            val superName = superDeclaration.qualifiedName?.asString()
            superName == ANDROID_VIEW_MODEL ||
                ((superDeclaration as? KSClassDeclaration)?.isAndroidViewModel(visited) == true)
        }
    }

    private fun generateViewModelRegistry() {
        val javaClassType = ClassName("java.lang", "Class").parameterizedBy(com.squareup.kotlinpoet.STAR)
        val registry = TypeSpec.objectBuilder(VIEW_MODEL_REGISTRY)
            .addProperty(
                PropertySpec.builder(
                    "viewModels",
                    ClassName("kotlin.collections", "List").parameterizedBy(javaClassType),
                )
                    .addModifiers(KModifier.PUBLIC)
                    .initializer(viewModels.values.toRegistryInitializer())
                    .build(),
            )
            .build()

        FileSpec.builder(GENERATED_PACKAGE, VIEW_MODEL_REGISTRY)
            .addFileComment("Generated by mobile-harness-ksp. Do not edit.")
            .addType(registry)
            .build()
            .writeTo(
                codeGenerator,
                Dependencies(true, *viewModelFiles.toTypedArray()),
            )
    }

    private fun Collection<KSClassDeclaration>.toRegistryInitializer(): CodeBlock =
        CodeBlock.builder()
            .add("listOf(\n")
            .indent()
            .apply { forEach { add("%T::class.java,\n", it.toClassName()) } }
            .unindent()
            .add(")")
            .build()

    private fun generateForFeature(feature: KSClassDeclaration) {
        val annotation = feature.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == MOBILE_FEATURE
        } ?: return

        val featureName = annotation.stringArgument("name")
        val description = annotation.stringArgument("description")

        if (featureName.isBlank()) {
            logger.error("@MobileFeature name must not be blank", feature)
            return
        }

        val directFeatureType = feature.superTypes
            .map { it.resolve() }
            .firstOrNull {
                it.declaration.qualifiedName?.asString() == FEATURE
            }

        if (directFeatureType == null) {
            logger.error(
                "${feature.simpleName.asString()} must directly implement Feature<State, Action, Effect> in v1",
                feature,
            )
            return
        }

        val typeArgs = directFeatureType.arguments
        if (typeArgs.size != 3 || typeArgs.any { it.type == null }) {
            logger.error("Feature must provide concrete State, Action and Effect types", feature)
            return
        }

        val stateType = typeArgs[0].type!!.resolve()
        val actionType = typeArgs[1].type!!.resolve()
        val effectType = typeArgs[2].type!!.resolve()

        val stateDeclaration = stateType.declaration as? KSClassDeclaration
        val actionDeclaration = actionType.declaration as? KSClassDeclaration
        val effectDeclaration = effectType.declaration as? KSClassDeclaration

        if (stateDeclaration == null || actionDeclaration == null || effectDeclaration == null) {
            logger.error("State, Action and Effect must resolve to classes/interfaces", feature)
            return
        }

        validateNoArgConstructor(feature)

        val packageName = feature.packageName.asString()
        val featureSimpleName = feature.simpleName.asString()
        val harnessName = "${featureSimpleName}_MobileHarness"
        val providerName = "${featureSimpleName}_MobileProvider"

        val harnessClass = ClassName(packageName, harnessName)
        val providerClass = ClassName(packageName, providerName)

        generateHarness(
            packageName = packageName,
            harnessName = harnessName,
            feature = feature,
            stateDeclaration = stateDeclaration,
            actionDeclaration = actionDeclaration,
            effectDeclaration = effectDeclaration,
            featureName = featureName,
            description = description,
        )

        generateProvider(
            packageName = packageName,
            providerName = providerName,
            harnessClass = harnessClass,
        )

        generatedProviderNames += providerClass.canonicalName
    }

    private fun validateNoArgConstructor(feature: KSClassDeclaration) {
        if (feature.classKind == ClassKind.OBJECT) return

        val constructor = feature.primaryConstructor
        if (constructor != null && constructor.parameters.any { !it.hasDefault }) {
            logger.error(
                "${feature.simpleName.asString()} must have a no-arg constructor (or all constructor parameters must have defaults) for generated harness v1",
                feature,
            )
        }
    }

    private fun generateHarness(
        packageName: String,
        harnessName: String,
        feature: KSClassDeclaration,
        stateDeclaration: KSClassDeclaration,
        actionDeclaration: KSClassDeclaration,
        effectDeclaration: KSClassDeclaration,
        featureName: String,
        description: String,
    ) {
        val generatedBase = ClassName(
            "com.example.mobileharness.runtime",
            "GeneratedFeatureHarness",
        ).parameterizedBy(
            stateDeclaration.asStarProjectedType().toTypeName(),
            actionDeclaration.asStarProjectedType().toTypeName(),
            effectDeclaration.asStarProjectedType().toTypeName(),
        )

        val descriptorClass = ClassName("com.example.mobileharness.runtime", "FeatureDescriptor")
        val actionDescriptorClass = ClassName("com.example.mobileharness.runtime", "ActionDescriptor")
        val parameterDescriptorClass = ClassName("com.example.mobileharness.runtime", "ParameterDescriptor")

        val descriptorCode = CodeBlock.builder()
            .add("%T(\n", descriptorClass)
            .indent()
            .add("name = %S,\n", featureName)
            .add("description = %S,\n", description)
            .add("actions = listOf(\n")
            .indent()

        actionDescriptors(actionDeclaration).forEach { action ->
            descriptorCode
                .add("%T(\n", actionDescriptorClass)
                .indent()
                .add("name = %S,\n", action.name)
                .add("parameters = listOf(\n")
                .indent()

            action.parameters.forEach { parameter ->
                descriptorCode.add(
                    "%T(name = %S, type = %S, required = %L),\n",
                    parameterDescriptorClass,
                    parameter.name,
                    parameter.type,
                    parameter.required,
                )
            }

            descriptorCode
                .unindent()
                .add("),\n")
                .unindent()
                .add("),\n")
        }

        descriptorCode
            .unindent()
            .add(")\n")
            .unindent()
            .add(")")

        val stateSerializer = CodeBlock.of("%T.serializer()", stateDeclaration.toClassName())
        val actionSerializer = CodeBlock.of("%T.serializer()", actionDeclaration.toClassName())
        val effectSerializer = CodeBlock.of("%T.serializer()", effectDeclaration.toClassName())

        val createFeature = FunSpec.builder("createFeature")
            .addModifiers(KModifier.OVERRIDE, KModifier.PROTECTED)
            .returns(feature.toClassName())
            .apply {
                if (feature.classKind == ClassKind.OBJECT) {
                    addStatement("return %T", feature.toClassName())
                } else {
                    addStatement("return %T()", feature.toClassName())
                }
            }
            .build()

        val type = TypeSpec.classBuilder(harnessName)
            .addModifiers(KModifier.PUBLIC)
            .superclass(generatedBase)
            .addSuperclassConstructorParameter("descriptor = %L", descriptorCode.build())
            .addSuperclassConstructorParameter("stateSerializer = %L", stateSerializer)
            .addSuperclassConstructorParameter("actionSerializer = %L", actionSerializer)
            .addSuperclassConstructorParameter("effectSerializer = %L", effectSerializer)
            .addFunction(createFeature)
            .build()

        FileSpec.builder(packageName, harnessName)
            .addFileComment("Generated by mobile-harness-ksp. Do not edit.")
            .addType(type)
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = false, feature.containingFile!!))
    }

    private fun generateProvider(
        packageName: String,
        providerName: String,
        harnessClass: ClassName,
    ) {
        val providerInterface = ClassName("com.example.mobileharness.runtime", "FeatureProvider")
        val featureHarness = ClassName("com.example.mobileharness.runtime", "FeatureHarness")

        val type = TypeSpec.classBuilder(providerName)
            .addModifiers(KModifier.PUBLIC)
            .addSuperinterface(providerInterface)
            .addFunction(
                FunSpec.builder("create")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(featureHarness)
                    .addStatement("return %T()", harnessClass)
                    .build(),
            )
            .build()

        FileSpec.builder(packageName, providerName)
            .addFileComment("Generated by mobile-harness-ksp. Do not edit.")
            .addType(type)
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = false))
    }

    private fun actionDescriptors(actionDeclaration: KSClassDeclaration): List<ActionMeta> {
        // V1 intentionally supports the common pattern where sealed action subclasses
        // are nested directly inside the sealed interface/class.
        return actionDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { child ->
                child.superTypes.any {
                    it.resolve().declaration.qualifiedName?.asString() == actionDeclaration.qualifiedName?.asString()
                }
            }
            .map { child ->
                ActionMeta(
                    name = child.serialNameOrDefault(),
                    parameters = child.primaryConstructor
                        ?.parameters
                        .orEmpty()
                        .map { it.toParameterMeta() },
                )
            }
            .sortedBy { it.name }
            .toList()
    }

    private fun KSClassDeclaration.serialNameOrDefault(): String {
        val serialName = annotations
            .firstOrNull {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIAL_NAME
            }
            ?.arguments
            ?.firstOrNull()
            ?.value as? String

        if (!serialName.isNullOrBlank()) return serialName

        val simple = simpleName.asString()
        return simple.replaceFirstChar { it.lowercase() }
    }

    private fun KSValueParameter.toParameterMeta(): ParameterMeta {
        val resolved = type.resolve()
        return ParameterMeta(
            name = name?.asString() ?: "arg",
            type = resolved.declaration.simpleName.asString() + if (resolved.nullability.name == "NULLABLE") "?" else "",
            required = !hasDefault && resolved.nullability.name != "NULLABLE",
        )
    }

    private fun writeServiceLoaderFile() {
        val dependencies = Dependencies(true, *originatingFiles.toTypedArray())

        // KSP non-source outputs are resources. Package dots become path separators,
        // yielding META-INF/services/<FeatureProvider FQCN> in the jar.
        val stream = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = "META-INF.services",
            fileName = FEATURE_PROVIDER,
            extensionName = "",
        )

        OutputStreamWriter(stream).use { writer ->
            generatedProviderNames.sorted().forEach { provider ->
                writer.appendLine(provider)
            }
        }
    }

    private fun KSAnnotation.stringArgument(name: String): String =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: ""

    private data class ActionMeta(
        val name: String,
        val parameters: List<ParameterMeta>,
    )

    private data class ParameterMeta(
        val name: String,
        val type: String,
        val required: Boolean,
    )
}
