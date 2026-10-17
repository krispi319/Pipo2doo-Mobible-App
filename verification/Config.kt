package ai.mwm.client
object SparkFlashConfig {const val API_KEY="synthetic-test-only"}
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() {
 androidx.compose.ui.ComposeUiFlags.isSemanticAutofillEnabled=true
 val app=SparkApplication();app.onCreate()
 check(!androidx.compose.ui.ComposeUiFlags.isSemanticAutofillEnabled)
 check(SparkApplication.spark is ai.mwm.spark.Spark)
 println("PASS: actual Compose UI 1.10.6 flag is accessible; fallback set before Application and Spark initialization; SDK initialization retained")
}
