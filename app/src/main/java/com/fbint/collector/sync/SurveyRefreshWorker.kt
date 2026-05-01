package com.fbint.collector.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fbint.collector.data.repository.SurveyRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SurveyRefreshWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: SurveyRepository,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val outcome = repo.refresh()
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "fbint.surveyRefresh"
        const val PERIODIC_NAME = "fbint.surveyRefresh.periodic"
    }
}
