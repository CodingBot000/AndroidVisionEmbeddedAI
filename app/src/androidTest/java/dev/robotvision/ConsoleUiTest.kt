package dev.robotvision
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.robotvision.presentation.design.RobotTheme
import dev.robotvision.presentation.navigation.*
import dev.robotvision.presentation.vision.*
import dev.robotvision.data.vision.EngineState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class ConsoleUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun dashboardStartScan() {
        var started=false
        compose.setContent { RobotTheme { DashboardScreen(VisionUiState(),emptyList(),{started=true},{}) } }
        compose.onNodeWithText("START LIVE SCAN").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(started) }
    }
    @Test fun errorDoesNotClaimReady() {
        compose.setContent { RobotTheme { DashboardScreen(VisionUiState(engine=EngineState(error="MODEL_UNAVAILABLE")),emptyList(),{},{}) } }
        compose.onNodeWithText("NOT READY").assertExists()
        compose.onNodeWithText("MODEL_UNAVAILABLE").performScrollTo().assertIsDisplayed()
    }
    @Test fun targetSelectionEmitsIntent() {
        var intent:VisionIntent?=null
        compose.setContent { RobotTheme { TargetChips(VisionUiState()) {intent=it} } }
        compose.onNodeWithText("컵").performClick()
        compose.runOnIdle { assertEquals(VisionIntent.SelectTarget("cup"),intent) }
    }
}
