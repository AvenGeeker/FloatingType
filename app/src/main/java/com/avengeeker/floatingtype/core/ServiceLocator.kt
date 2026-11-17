package com.avengeeker.floatingtype.core

import android.content.Context
import com.avengeeker.floatingtype.asr.DashScopeAsrEngine
import com.avengeeker.floatingtype.audio.RecorderController
import com.avengeeker.floatingtype.history.HistoryRepository
import com.avengeeker.floatingtype.overlay.OverlayBallManager
import com.avengeeker.floatingtype.permission.PermissionRepository
import com.avengeeker.floatingtype.pipeline.VoiceInputPipeline
import com.avengeeker.floatingtype.polish.OpenAiCompatiblePolishEngine
import com.avengeeker.floatingtype.settings.SettingsRepository
import com.avengeeker.floatingtype.translate.OpenAiCompatibleTranslateEngine

/**
 * Lightweight manual service locator. Everything is application-scoped;
 * the accessibility service is the always-on backbone and registers itself
 * with the pipeline when connected.
 */
object ServiceLocator {
    lateinit var appContext: Context
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var permissions: PermissionRepository
        private set
    lateinit var recorder: RecorderController
        private set
    lateinit var history: HistoryRepository
        private set
    lateinit var pipeline: VoiceInputPipeline
        private set
    lateinit var overlay: OverlayBallManager
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        settings = SettingsRepository(appContext)
        permissions = PermissionRepository(appContext)
        recorder = RecorderController(appContext)
        history = HistoryRepository(appContext)
        pipeline = VoiceInputPipeline(
            context = appContext,
            settings = settings,
            history = history,
            asrFactory = { DashScopeAsrEngine(settings) },
            polishFactory = { OpenAiCompatiblePolishEngine(settings) },
            translateFactory = { OpenAiCompatibleTranslateEngine(settings) },
        )
        overlay = OverlayBallManager(appContext, settings, pipeline)
        pipeline.overlay = overlay
    }
}
