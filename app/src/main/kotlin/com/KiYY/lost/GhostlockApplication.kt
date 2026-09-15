package com.KiYY.lost

import android.app.Application
import com.KiYY.lost.data.AndroidGhostlockRepository
import com.KiYY.lost.domain.repository.GhostlockRepository

/** Application composition root. It is the only place that binds data implementations to domain ports. */
class GhostlockApplication : Application() {
    fun createRepository(): GhostlockRepository = AndroidGhostlockRepository(this)
}
