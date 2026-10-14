package ai.mwm.client

import ai.mwm.spark.Spark
import android.app.Application

// MWM Spark Flash: analytics + purchase validation, wired once for the whole
// process. Managed by sparkai-orchestrator — the SDK dependency, the vendored
// repo and SparkFlashConfig are maintained automatically; do not remove.
class SparkApplication : Application() {

    override fun onCreate() {
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
