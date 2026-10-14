package ai.mwm.client

import ai.mwm.client.ui.screens.HomeScreenPreview
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class PreviewScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun contentViewPreview() {
        composeTestRule.setContent {
            HomeScreenPreview()
        }
        composeTestRule.onRoot()
            .captureRoboImage("build/outputs/roborazzi/preview.png")
    }
}
