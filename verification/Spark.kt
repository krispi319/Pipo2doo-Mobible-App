package ai.mwm.spark
class Spark(block:Builder.()->Unit) {
 class Builder {fun apiKey(value:String){};fun application(app:android.app.Application){}}
 init {
  @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
  check(!androidx.compose.ui.ComposeUiFlags.isSemanticAutofillEnabled){"Fallback must be set before SDK init"}
  Builder().block()
 }
}
