package io.github.ocpabdurrahim.skilltrack

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import io.github.ocpabdurrahim.skilltrack.data.Approvals
import io.github.ocpabdurrahim.skilltrack.data.CenterStore
import io.github.ocpabdurrahim.skilltrack.data.SessionManager
import io.github.ocpabdurrahim.skilltrack.data.Settings
import io.github.ocpabdurrahim.skilltrack.data.SettingsStore
import io.github.ocpabdurrahim.skilltrack.system.Photos
import io.github.ocpabdurrahim.skilltrack.update.AppUpdater
import io.github.ocpabdurrahim.skilltrack.update.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SkillApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        configureFirebase()
        container = AppContainer(this)
        container.session.start()
        container.appScope.launch {
            val settings = container.settings.settings.first()
            container.updater.cleanup()
            if (settings.autoUpdate) {
                UpdateWorker.schedule(this@SkillApp)
                container.updater.checkAndDownload()
            }
        }
    }

    private fun configureFirebase() {
        val host = BuildConfig.FIREBASE_EMULATOR_HOST
        if (host.isNotEmpty()) {
            Firebase.auth.useEmulator(host, 9099)
            Firebase.firestore.useEmulator(host, 8080)
        }
        // The cache is the phone's copy of the center. It must never be trimmed:
        // the listeners only ask for newer documents, so an evicted one would not come back.
        Firebase.firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) })
        }
    }
}

/** Hand-wired dependencies, one of each per process. */
class AppContainer(val app: SkillApp) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val settingsState: StateFlow<Settings?> = settings.settings.stateIn(appScope, SharingStarted.Eagerly, null)
    val store = CenterStore()
    val session = SessionManager(settings, store, appScope)
    val photos = Photos(store)
    val approvals = Approvals()
    val updater = AppUpdater(app, settings, appScope)
    val appLock = AppLockState()
}

/**
 * Whether the PIN screen is showing. Locks on a cold start and after the app has
 * been in the background for longer than [GRACE_MS]; trips to the photo picker
 * or the print dialog call [skipNextLock] so they do not ask again.
 */
class AppLockState {
    var unlocked by mutableStateOf(false)
    private var backgroundAt = 0L
    private var skipNext = false

    fun skipNextLock() {
        skipNext = true
    }

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val away = backgroundAt != 0L && SystemClock.elapsedRealtime() - backgroundAt > GRACE_MS
        backgroundAt = 0L
        if (skipNext) {
            skipNext = false
            return
        }
        if (away) unlocked = false
    }

    private companion object {
        const val GRACE_MS = 10_000L
    }
}
