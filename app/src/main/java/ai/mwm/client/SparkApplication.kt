package ai.mwm.client

import ai.mwm.spark.Spark
import android.app.Application
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi

// MWM Spark Flash: analytics + purchase validation, wired once for the whole
// process. Managed by sparkai-orchestrator — the SDK dependency, the vendored
// repo and SparkFlashConfig are maintained automatically; do not remove.
class SparkApplication : Application() {

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate() {
        // Temporary workaround for ChildSemanticsNode traversing a detached node
        // through eager semantics invalidation (Compose UI 1.10.6 / Material3 1.4.0).
        // Set before any Compose content or SDK initialization. This disables the
        // new semantic Autofill path; it does not disable accessibility services.
        ComposeUiFlags.isSemanticAutofillEnabled = false
        super.onCreate()
        spark = Spark {
            apiKey(SparkFlashConfig.API_KEY)
            application(this@SparkApplication)
        }
    }

    companion object {
        // The single Spark instance for the process lifetime. Reach analytics via
        // spark.analytics and purchase recording via spark.monetization.
        lateinit var spark: Spark
    }
}
