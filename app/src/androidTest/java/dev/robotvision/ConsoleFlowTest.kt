package dev.robotvision
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import org.junit.Rule
import org.junit.Test
import java.io.File
class ConsoleFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String) {
        val image=compose.onRoot().captureToImage().asAndroidBitmap()
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"screenshots").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun simulationMissionAndHistoryDeletion() {
        compose.onNodeWithText("Mission control").assertExists()
        screenshot("dashboard")
        compose.onNodeWithText("진단").performClick()
        compose.onNodeWithText("Debug 시뮬레이션 시작").performScrollTo().performClick()
        compose.onNodeWithText("SIMULATION / FAKE DATA").assertExists()
        compose.onNodeWithText("비전").performClick()
        compose.onNodeWithText("컵").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("대상을 발견했습니다").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("SIMULATION").performScrollTo()
        screenshot("vision-simulation")
        compose.onNodeWithText("기록").performClick()
        compose.waitUntil(5_000) {compose.onAllNodesWithText("FOUND").fetchSemanticsNodes().isNotEmpty()}
        screenshot("history-simulation")
        compose.onNodeWithText("전체 기록 삭제").performClick()
        compose.onNodeWithText("기록을 삭제할까요?").assertExists()
        compose.onNodeWithText("삭제",useUnmergedTree=true).performClick()
        compose.waitUntil(5_000) {compose.onAllNodesWithText("기록 대기 중").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("Console settings").assertExists()
        screenshot("settings")
        compose.onNodeWithText("진단").performClick()
        compose.onNodeWithText("실제 엔진 모드로 전환").performScrollTo().performClick()
        compose.onNodeWithText("System health").performScrollTo()
        screenshot("diagnostics")
    }
}
