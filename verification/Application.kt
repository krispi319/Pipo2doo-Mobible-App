package android.app
open class Application {
 open fun onCreate() {
  @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
  check(!androidx.compose.ui.ComposeUiFlags.isSemanticAutofillEnabled){"Fallback must be set before super.onCreate"}
 }
}
