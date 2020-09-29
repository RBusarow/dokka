package org.jetbrains.dokka.plugability

import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.utilities.parseJson
import org.jetbrains.dokka.utilities.toJsonString
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createInstance

abstract class DokkaPlugin {
    private val extensionDelegates = mutableListOf<KProperty<*>>()

    @PublishedApi
    internal var context: DokkaContext? = null

    protected inline fun <reified T : DokkaPlugin> plugin(): T = context?.plugin(T::class) ?: throwIllegalQuery()

    protected fun <T : Any> extensionPoint() = ReadOnlyProperty<DokkaPlugin, ExtensionPoint<T>> { thisRef, property ->
        ExtensionPoint(
            thisRef::class.qualifiedName ?: throw AssertionError("Plugin must be named class"),
            property.name
        )
    }

    protected fun <T : Any> extending(definition: ExtendingDSL.() -> Extension<T, *, *>) = ExtensionProvider(definition)

    protected class ExtensionProvider<T : Any> internal constructor(
        private val definition: ExtendingDSL.() -> Extension<T, *, *>
    ) {
        operator fun provideDelegate(thisRef: DokkaPlugin, property: KProperty<*>) = lazy {
            ExtendingDSL(
                thisRef::class.qualifiedName ?: throw AssertionError("Plugin must be named class"),
                property.name
            ).definition()
        }.also { thisRef.extensionDelegates += property }
    }

    internal fun internalInstall(ctx: DokkaContextConfiguration, configuration: DokkaConfiguration) {
        extensionDelegates.asSequence()
            .filterIsInstance<KProperty1<DokkaPlugin, Extension<*, *, *>>>() // should be always true
            .map { it.get(this) }
            .forEach { if (configuration.(it.condition)()) ctx.installExtension(it) }
    }
}

interface WithUnsafeExtensionSuppression {
    val extensionsSuppressed: List<Extension<*, *, *>>
}

interface Configurable {
    val pluginsConfiguration: MutableList<DokkaConfiguration.PluginConfiguration>
}

interface ConfigurableBlock

inline fun <reified P : DokkaPlugin, reified T : ConfigurableBlock> Configurable.pluginConfiguration(block: T.() -> Unit) {
    val instance = T::class.createInstance().apply(block)
    val pluginConfiguration = PluginConfigurationImpl(
        fqPluginName = P::class.qualifiedName!!,
        serializedType = DokkaConfiguration.SerializedType.JSON,
        values = toJsonString(instance)
    )
    pluginsConfiguration.add(pluginConfiguration)
}

inline fun <reified P : DokkaPlugin, reified E : Any> P.query(extension: P.() -> ExtensionPoint<E>): List<E> =
    context?.let { it[extension()] } ?: throwIllegalQuery()

inline fun <reified P : DokkaPlugin, reified E : Any> P.querySingle(extension: P.() -> ExtensionPoint<E>): E =
    context?.single(extension()) ?: throwIllegalQuery()

fun throwIllegalQuery(): Nothing =
    throw IllegalStateException("Querying about plugins is only possible with dokka context initialised")

inline fun <reified T : DokkaPlugin, reified R : ConfigurableBlock> configuration(context: DokkaContext): R? =
    context.configuration.pluginsConfiguration.firstOrNull { it.fqPluginName == T::class.qualifiedName }
        ?.let { configuration ->
            when (configuration.serializedType) {
                DokkaConfiguration.SerializedType.JSON -> parseJson(configuration.values)
                else -> XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(true) }).readValue<R>(configuration.values)
            }
        }
