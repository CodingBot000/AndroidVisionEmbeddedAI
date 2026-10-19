package dev.robotvision.presentation
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.robotvision.presentation.design.RobotTheme
import dev.robotvision.presentation.navigation.DashboardScreen
import dev.robotvision.presentation.vision.VisionUiState
import dev.robotvision.domain.*
import dev.robotvision.data.vision.EngineState
@Preview(name="Initial · 360dp",widthDp=360,heightDp=800,showBackground=true)
@Composable fun InitialPreview() { RobotTheme { DashboardScreen(VisionUiState(),emptyList(),{},{}) } }
@Preview(name="Searching",widthDp=360,heightDp=800)
@Composable fun SearchPreview() { RobotTheme { DashboardScreen(VisionUiState(search=SearchState(Targets.byLabel("cup"),SearchStatus.SEARCHING),simulation=true),emptyList(),{},{}) } }
@Preview(name="Found",widthDp=360,heightDp=800)
@Composable fun FoundPreview() { RobotTheme { DashboardScreen(VisionUiState(search=SearchState(Targets.byLabel("cup"),SearchStatus.FOUND),simulation=true),listOf(Event(time=0,type="FOUND",label="cup",simulated=true)),{},{}) } }
@Preview(name="Model error",widthDp=360,heightDp=800)
@Composable fun ErrorPreview() { RobotTheme { DashboardScreen(VisionUiState(engine=EngineState(error="MODEL_UNAVAILABLE: 모델 무결성을 확인하세요.")),emptyList(),{},{}) } }
