package com.kascorp.webhooknotesender

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.kascorp.webhooknotesender.data.remote.WebhookApi
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import com.kascorp.webhooknotesender.util.LocaleHelper
import com.kascorp.webhooknotesender.work.QueueWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class WebhookNoteSenderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var queueRepository: QueueRepository

    @Inject
    lateinit var webhookApi: WebhookApi

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(ChainedWorkerFactory(hiltWorkerFactory, queueRepository, webhookApi))
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun attachBaseContext(base: Context) {
        LocaleHelper.init(base)
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        QueueWorker.enqueue(this)
    }
}

private class ChainedWorkerFactory(
    private val hiltFactory: HiltWorkerFactory,
    private val queueRepository: QueueRepository,
    private val webhookApi: WebhookApi
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            QueueWorker::class.java.name -> QueueWorker(
                appContext, workerParameters, queueRepository, webhookApi
            )
            else -> hiltFactory.createWorker(appContext, workerClassName, workerParameters)
        }
    }
}
