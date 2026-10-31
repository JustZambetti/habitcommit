package dev.habitcommit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dev.habitcommit.ui.HabitApp
import dev.habitcommit.ui.HabitCommitTheme
import dev.habitcommit.widget.WidgetRefreshScheduler
import dev.habitcommit.widget.refreshAllWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val route = MutableStateFlow<String?>(null)

    /** Bumped on every resume so the Compose UI reloads state changed from the widget. */
    private val resumeTick = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route.value = intent?.data?.toString()
        val app = application as HabitCommitApp
        val repository = app.repository

        setContent {
            val currentRoute by route.collectAsState()
            val resume by resumeTick.collectAsState()
            HabitCommitTheme {
                HabitApp(
                    repository = repository,
                    syncManager = app.syncManager,
                    route = currentRoute,
                    resumeTick = resume,
                    onRouteConsumed = { route.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route.value = intent.data?.toString()
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value += 1
        WidgetRefreshScheduler.ensureScheduled(this)
        lifecycleScope.launch {
            refreshAllWidgets(this@MainActivity)
        }
    }
}
