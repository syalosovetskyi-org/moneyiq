package org.syalosovetskyi.onemoney.workers

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import org.syalosovetskyi.onemoney.BuildConfig
import org.syalosovetskyi.onemoney.data.repository.AppSettings

internal fun monoFlowConfigured(settings: AppSettings): Boolean =
    BuildConfig.MONOFLOW_ENABLED && settings.monoflowUrl.isNotBlank() && settings.monoflowToken.isNotBlank()

internal suspend fun monoFlowConfigured(context: Context): Boolean =
    BuildConfig.MONOFLOW_ENABLED && monoFlowConfigured(EntryPointAccessors.fromApplication(
        context.applicationContext, MonoFlowSyncEntryPoint::class.java).settingsRepository().settings.first())
