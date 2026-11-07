package ai.mwm.client.ui.screens

import ai.mwm.client.cloud.*
import ai.mwm.client.data.*
import ai.mwm.client.ui.theme.*
import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[28],application=Application::class,qualifiers="w360dp-h800dp-xxhdpi")
class RestoreBackupUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun restoreRequiresConfirmationAndReportsFailureThenSuccess() {
        var attempts=0
        var backups=0
        compose.setContent {CompositionLocalProvider(LocalInspectionMode provides true,LocalPartnerConnection provides PartnerConnection(uid="me",spaceId="ours",authenticated=true,online=true)) {
            MyApplicationTheme {Surface(Modifier.fillMaxSize(),color=AppTheme.colors.background) {
                SettingsScreen(PipoState(preferences=Preferences(started=true)),true,{}, {}, {}, {}, {},onBackup={backups++},onRestoreBackup={done->attempts++;done(if(attempts==1)"No saved account backup was found." else null)})
            }}
        }}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Restore latest"))
        compose.onNodeWithText("Back up").performClick()
        compose.runOnIdle {assertEquals(1,backups)}
        compose.onNodeWithContentDescription("View status").performClick()
        compose.onNodeWithText("Backup status").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Restore latest").performClick()
        assertEquals(0,attempts)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0,attempts)
        compose.onNodeWithText("Restore latest").performClick()
        compose.onNodeWithText("Restore").performClick()
        compose.onNodeWithText("No saved account backup was found.").assertExists()
        compose.onNodeWithText("Restore latest").performClick()
        compose.onNodeWithText("Restore").performClick()
        compose.onNodeWithText("Latest backup restored ✓").assertExists()
        assertEquals(2,attempts)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/v145-restore-backup.png")
    }
}
