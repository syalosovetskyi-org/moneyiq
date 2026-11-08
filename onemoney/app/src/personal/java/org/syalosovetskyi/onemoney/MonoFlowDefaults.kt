package org.syalosovetskyi.onemoney

internal object MonoFlowDefaults {
    val url = if (BuildConfig.DEBUG) BuildConfig.DEBUG_MONOFLOW_URL else ""
    val token = if (BuildConfig.DEBUG) BuildConfig.DEBUG_MONOFLOW_TOKEN else ""
}
