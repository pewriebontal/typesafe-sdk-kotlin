package net.bontal.typesafesdk.internal

import net.bontal.typesafesdk.TypeSafeLogger

internal expect fun platformRuntime(): String

internal expect fun getEnvironmentVariable(name: String): String?

internal expect fun getSystemProperty(name: String): String?

internal expect fun checkNotMainThread()

internal expect fun defaultLogger(): TypeSafeLogger

internal fun readSetting(property: String, environmentVariable: String): String? =
    getSystemProperty(property)?.trim()?.ifEmpty { null }
        ?: getEnvironmentVariable(environmentVariable)?.trim()?.ifEmpty { null }
