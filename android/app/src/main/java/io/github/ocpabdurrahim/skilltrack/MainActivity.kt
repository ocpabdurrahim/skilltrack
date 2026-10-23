package io.github.ocpabdurrahim.skilltrack

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.ocpabdurrahim.skilltrack.ui.SkillRoot

// FragmentActivity rather than ComponentActivity: BiometricPrompt needs it.
class MainActivity : FragmentActivity() {
    private val container: AppContainer get() = (application as SkillApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SkillRoot(container) }
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground()
        container.updater.onAppForeground()
        // "Today" may have moved on while the app was in the background.
        container.store.refresh()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            container.appLock.onBackground()
            // A downloaded update installs once the app is out of the way.
            container.updater.onAppBackground()
        }
    }
}
