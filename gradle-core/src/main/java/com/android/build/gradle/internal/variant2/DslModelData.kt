/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.variant2

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.DefaultConfig
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.api.dsl.model.BuildTypeFactory
import com.android.build.gradle.internal.api.dsl.model.BuildTypeImpl
import com.android.build.gradle.internal.api.dsl.model.DefaultConfigImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorFactory
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.options.SigningConfigFactory
import com.android.build.gradle.internal.api.dsl.options.SigningConfigImpl
import com.android.build.gradle.internal.api.dsl.sealing.Sealable
import com.android.build.gradle.internal.api.sourcesets.AndroidSourceSetFactory
import com.android.build.gradle.internal.api.sourcesets.DefaultAndroidSourceSet
import com.android.build.gradle.internal.api.sourcesets.FilesProvider
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreLocation
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.errors.EvalIssueException
import com.android.builder.core.VariantTypeImpl
import com.android.builder.errors.EvalIssueReporter.Type
import com.android.utils.appendCapitalized
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import java.util.function.BinaryOperator

/**
 * Internal DSL model exposed to the extension objects.
 */
interface DslModelData {
    val defaultConfig: DefaultConfig
    val sourceSets: NamedDomainObjectContainer<out AndroidSourceSet>
    val productFlavors: NamedDomainObjectContainer<ProductFlavor>
    val buildTypes: NamedDomainObjectContainer<BuildType>
    val signingConfigs: NamedDomainObjectContainer<SigningConfig>
}

/**
 * a Factory of [NamedDomainObjectContainer]
 *
 * This is to facilitate testing.
 */
interface ContainerFactory {
    /**
     * Creates a container
     *
     * @param itemClass the class of the items.
     * @param factory a factory to create items.
     */
    fun <T, U : T> createContainer(itemClass: Class<U>,
            factory: NamedDomainObjectFactory<T>): NamedDomainObjectContainer<T>
}

/**
 * Implementation of the DslModelData interface
 */
class DslModelDataImpl<in E: BaseExtension2>(
        override val defaultConfig: DefaultConfigImpl,
        internal val variantFactories: List<VariantFactory2<E>>,
        private val configurationContainer: ConfigurationContainer,
        filesProvider: FilesProvider,
        containerFactory: ContainerFactory,
        private val dslScope: DslScope,
        private val logger: Logger): DslModelData, Sealable {

    // wrapped container for source sets.
    override val sourceSets: NamedDomainObjectContainer<DefaultAndroidSourceSet>

    // wrapped container for product flavors
    override val productFlavors: NamedDomainObjectContainer<ProductFlavor> =
            containerFactory.createContainer(
                    ProductFlavorImpl::class.java,
                    ProductFlavorFactory(dslScope))

    // wrapped container for build type
    override val buildTypes: NamedDomainObjectContainer<BuildType> =
            containerFactory.createContainer(
                    BuildTypeImpl::class.java,
                    BuildTypeFactory(dslScope))

    // wrapped container for signing config
    override val signingConfigs: NamedDomainObjectContainer<SigningConfig> =
            containerFactory.createContainer(
                    SigningConfigImpl::class.java,
                    SigningConfigFactory(
                            dslScope,
                            getDefaultDebugKeystoreLocation()))

    private val _flavorData: MutableMap<String, DimensionData<ProductFlavor>> = mutableMapOf()
    private val _buildTypeData: MutableMap<String, DimensionData<BuildType>> = mutableMapOf()

    private var afterEvaluatedComputation = false

    val flavorData: Map<String, DimensionData<ProductFlavor>>
        get() {
            if (!afterEvaluatedComputation) throw RuntimeException("Called before afterEvaluateCompute")
            return _flavorData
        }
    val buildTypeData: Map<String, DimensionData<BuildType>>
        get() {
            if (!afterEvaluatedComputation) throw RuntimeException("Called before afterEvaluateCompute")
            return _buildTypeData
        }

    val defaultConfigData: DimensionData<DefaultConfigImpl>

    private val mainVariantType: VariantType
    private val hasAndroidTests: Boolean
    private val hasUnitTests: Boolean

    init {
        // detect the test level support
        val variantTypes = variantFactories.map { it.generatedType }

        mainVariantType = variantTypes
                .stream()
                .filter { !it.isTestComponent }
                .reduce(toSingleItem())
                .orElseThrow { RuntimeException("No main variant type") }

        sourceSets = containerFactory.createContainer(
                DefaultAndroidSourceSet::class.java,
                AndroidSourceSetFactory(
                        filesProvider,
                        mainVariantType.isAar,
                        dslScope))

        hasAndroidTests = variantTypes.contains(VariantTypeImpl.ANDROID_TEST)
        hasUnitTests = variantTypes.contains(VariantTypeImpl.UNIT_TEST)

        // setup callback to generate source sets on the fly, as well as the associated
        // configurations
        productFlavors.whenObjectAdded { checkNewFlavor(it) }
        buildTypes.whenObjectAdded { checkNewBuildType(it) }
        sourceSets.whenObjectAdded { handleNewSourceSet(it as DefaultAndroidSourceSet) }

        // map whenObjectRemoved on the containers to throw an exception.
        val lambda: (Any) -> Unit = { UnsupportedOperationException("Removing objects is not supported.") }
        sourceSets.whenObjectRemoved(lambda)
        signingConfigs.whenObjectRemoved(lambda)
        buildTypes.whenObjectRemoved(lambda)
        productFlavors.whenObjectRemoved(lambda)

        // and now create source set and dimension data for the default config
        createSourceSets(BuilderConstants.MAIN)
        defaultConfigData = createDimensionData(defaultConfig, { _ -> BuilderConstants.MAIN} )
    }

    /**
     * Does afterEvaluation computation of source sets and flavor/build type data.
     */
    fun afterEvaluateCompute() {
        // loop on flavors and build types.
        productFlavors.forEach { flavor ->
            _flavorData[flavor.name] = createDimensionData(flavor, { it.name })
        }

        buildTypes.forEach { buildType ->
            _buildTypeData[buildType.name] = createDimensionData(buildType, { it.name})
        }

        afterEvaluatedComputation = true
    }

    override fun seal() {
        defaultConfig.seal()
    }

    private fun <T> createDimensionData(data: T, nameFun: (T) -> String): DimensionData<T> {
        val name = nameFun(data)

        return DimensionData(
                data,
                sourceSets.getByName(name), // this one must exist, so use getByName
                sourceSets.findByName(computeSourceSetName(name, VariantTypeImpl.ANDROID_TEST)), // this one might not, so use findByName
                sourceSets.findByName(computeSourceSetName(name, VariantTypeImpl.UNIT_TEST)), // this one might not, so use findByName
                configurationContainer)
    }

    /**
     * Callback for all new added product flavor.
     *
     * Checks its for correctness and creates its associated source sets.
     */
    private fun checkNewFlavor(productFlavor: ProductFlavor) {
        val name = productFlavor.name

        if (!checkName(name, "ProductFlavor")) {
            // don't want to keep going in case of sync
            return
        }

        if (buildTypes.any { it.name == name }) {
            dslScope.issueReporter.reportError(Type.GENERIC,
                EvalIssueException("ProductFlavor names cannot collide with BuildType names: $name"))

            // don't want to keep going in case of sync
            return
        }

        // create sourcesets
        createSourceSets(name)
    }

    /**
     * Callback for all new added build type.
     *
     * Checks its for correctness and creates its associated source sets.
     */
    private fun checkNewBuildType(buildType: BuildType) {
        val name = buildType.name

        if (!checkName(name, "BuildType")) {
            // don't want to keep going in case of sync
            return
        }

        if (productFlavors.any { it.name == name }) {
            dslScope.issueReporter.reportError(Type.GENERIC,
                EvalIssueException("BuildType names cannot collide with ProductFlavor names: $name"))

            // don't want to keep going in case of sync
            return
        }

        // create sourcesets
        createSourceSets(name)
    }

    /** callback for creating sourcesets when a product flavor/build type is added. */
    private fun createSourceSets(name: String) {
        // safe to use the backing container directly since this is called on new flavor
        // or build type.
        sourceSets.maybeCreate(name)

        if (hasAndroidTests) {
            sourceSets.maybeCreate(computeSourceSetName(name, VariantTypeImpl.ANDROID_TEST))
        }

        if (hasUnitTests) {
            sourceSets.maybeCreate(computeSourceSetName(name, VariantTypeImpl.UNIT_TEST))
        }
    }

    /**
     * Callback for all newly added sourcesets
     */
    private fun handleNewSourceSet(sourceSet: DefaultAndroidSourceSet) {
        // set the default location of the source set
        sourceSet.setRoot("src/${sourceSet.name}")

        // create the associated configurations
        val implementationName = sourceSet.implementationConfigurationName
        val runtimeOnlyName = sourceSet.runtimeOnlyConfigurationName
        val compileOnlyName = sourceSet.compileOnlyConfigurationName

        // deprecated configurations first.
        val compileName = sourceSet._compileConfigurationName
        // due to compatibility with other plugins and with Gradle sync,
        // we have to keep 'compile' as resolvable.
        // TODO Fix this in gradle sync.
        val compile = createConfiguration(
                configurationContainer,
                compileName,
                getConfigDescriptionOld("compile", sourceSet.name, implementationName),
                "compile" == compileName || "testCompile" == compileName /*canBeResolved*/)
        compile.dependencies
                .whenObjectAdded(
                        RenamedConfigurationAction(implementationName, compileName, dslScope.deprecationReporter))

        val packageConfigDescription = if (mainVariantType.isAar) {
            getConfigDescriptionOld("publish", sourceSet.name, runtimeOnlyName)
        } else {
            getConfigDescriptionOld("apk", sourceSet.name, runtimeOnlyName)
        }

        val apkName = sourceSet._packageConfigurationName
        val apk = createConfiguration(
                configurationContainer, apkName, packageConfigDescription)
        apk.dependencies
                .whenObjectAdded(
                        RenamedConfigurationAction(
                                runtimeOnlyName, apkName, dslScope.deprecationReporter))

        val providedName = sourceSet._providedConfigurationName
        val provided = createConfiguration(
                configurationContainer,
                providedName,
                getConfigDescriptionOld("provided", sourceSet.name, compileOnlyName))
        provided.dependencies
                .whenObjectAdded(
                        RenamedConfigurationAction(
                                compileOnlyName, providedName, dslScope.deprecationReporter))

        // then the new configurations.
        val apiName = sourceSet.apiConfigurationName
        val api = createConfiguration(
                configurationContainer, apiName, "API dependencies for '${sourceSet.name}' sources.")
        api.extendsFrom(compile)

        val implementation = createConfiguration(
                configurationContainer,
                implementationName,
                getConfigDescription("implementation", sourceSet.name))
        implementation.extendsFrom(api)

        val runtimeOnly = createConfiguration(
                configurationContainer,
                runtimeOnlyName,
                getConfigDescription("RuntimeOnly", sourceSet.name))
        runtimeOnly.extendsFrom(apk)

        val compileOnly = createConfiguration(
                configurationContainer,
                compileOnlyName,
                getConfigDescription("compileOnly", sourceSet.name))
        compileOnly.extendsFrom(provided)

        // then the secondary configurations.
        createConfiguration(
                configurationContainer,
                sourceSet.wearAppConfigurationName,
                "Link to a wear app to embed for object '"
                        + sourceSet.name
                        + "'.")

        createConfiguration(
                configurationContainer,
                sourceSet.annotationProcessorConfigurationName,
                "Classpath for the annotation processor for '"
                        + sourceSet.name
                        + "'.")
    }


    /**
     * Creates a Configuration for a given source set.
     *
     * @param configurationContainer the configuration container to create the new configuration
     * @param name the name of the configuration to create.
     * @param desc the configuration description.
     * @param canBeResolved Whether the configuration can be resolved directly.
     * @return the configuration
     *
     * @see Configuration.isCanBeResolved
     */
    private fun createConfiguration(
            configurationContainer: ConfigurationContainer,
            name: String,
            desc: String,
            canBeResolved: Boolean = false): Configuration {
        logger.debug("Creating configuration {}", name)

        val configuration = configurationContainer.maybeCreate(name)

        with(configuration) {
            isVisible = false
            description = desc
            isCanBeConsumed = false
            isCanBeResolved = canBeResolved
        }

        return configuration
    }

    private fun checkName(name: String, displayName: String): Boolean {
        if (!checkPrefix(name, displayName, VariantType.ANDROID_TEST_PREFIX)) {
            return false
        }
        if (!checkPrefix(name, displayName, VariantType.UNIT_TEST_PREFIX)) {
            return false
        }

        if (BuilderConstants.MAIN == name) {
            dslScope.issueReporter.reportError(Type.GENERIC,
                EvalIssueException("$displayName names cannot be '${BuilderConstants.MAIN}'"))
            return false
        }

        if (BuilderConstants.LINT == name) {
            dslScope.issueReporter.reportError(Type.GENERIC,
                EvalIssueException("$displayName names cannot be '${BuilderConstants.LINT}'"))
            return false
        }

        return true
    }

    private fun checkPrefix(name: String, displayName: String, prefix: String): Boolean {
        if (name.startsWith(prefix)) {
            dslScope.issueReporter.reportError(Type.GENERIC,
                EvalIssueException("$displayName names cannot start with '$prefix'"))
            return false
        }

        return true
    }
}

/**
 * Turns a string into a valid source set name for the given [VariantType], e.g.
 * "fooBarUnitTest" becomes "testFooBar".
 *
 * This does not support MAIN.
 */
private fun computeSourceSetName(
        name: String,
        variantType: VariantType): String {
    if (name == BuilderConstants.MAIN) {
        if (variantType.prefix.isEmpty()) {
            return name
        }
        return variantType.prefix
    }

    var newName = name
    if (newName.endsWith(variantType.suffix)) {
        newName = newName.substring(0, newName.length - variantType.suffix.length)
    }

    if (!variantType.prefix.isEmpty()) {
        newName = buildString {
            append(variantType.prefix)
            appendCapitalized(newName)
        }
    }

    return newName
}

class RenamedConfigurationAction(
        private val replacement: String,
        private val oldName: String,
        private val deprecationReporter: DeprecationReporter,
        private val url: String? = null,
        private val deprecationTarget: DeprecationReporter.DeprecationTarget = DeprecationReporter.DeprecationTarget.CONFIG_NAME) : Action<Dependency> {
    private var warningPrintedAlready = false

    override fun execute(dependency: Dependency) {
        if (!warningPrintedAlready) {
            warningPrintedAlready = true
            deprecationReporter.reportRenamedConfiguration(
                    replacement, oldName, deprecationTarget, url)
        }
    }
}

/**
 * The goal of this operator is not to reduce anything but to ensure that
 * there is a single item in the list. If it gets called it means
 * that there are two object in the list that had the same name, and this is an error.
 *
 * @see .searchForSingleItemInList
 */
private fun <T> toSingleItem(): BinaryOperator<T> {
    return BinaryOperator { name1, _ -> throw IllegalArgumentException("Duplicate objects with name: $name1") }
}


private inline fun getConfigDescriptionOld(
        configName: String,
        sourceSetName: String,
        replacementName: String) =
        "$configName dependencies for '$sourceSetName' sources (deprecated: use '$replacementName' instead)."

private inline fun getConfigDescription(configName: String, sourceSetName: String) =
        "$configName dependencies for '$sourceSetName' sources."
