package dev.robotvision.presentation.vision

import android.graphics.Paint
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.robotvision.data.camera.CameraSession
import dev.robotvision.debug.DebugFixtureSurface
import dev.robotvision.domain.*
import dev.robotvision.domain.Target
import dev.robotvision.presentation.design.*
import dev.robotvision.presentation.navigation.*

@Composable fun LiveVisionScreen(vm: VisionViewModel, state: VisionUiState, metrics: Metrics,
    requestCamera: () -> Unit, requestMicrophone: () -> Unit, openSettings: () -> Unit) {
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { vm.dispatch(VisionIntent.CancelListening); vm.dispatch(VisionIntent.StopSearch) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val cameraHeight = (maxHeight * .58f).coerceIn(260.dp, 480.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(cameraHeight).background(Panel)) {
            if (state.simulation) DebugFixtureSurface()
            else if (state.cameraPermission) CameraPreview(vm)
            else Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.VideocamOff,null,tint=Muted,modifier=Modifier.size(40.dp))
                Text("카메라 권한이 필요합니다")
                Text("주변의 물체를 기기 안에서만 분석합니다.",color=Muted)
                Button(onClick=requestCamera) { Text("카메라 허용") }
                TextButton(onClick=openSettings) { Text("앱 권한 설정") }
            }
            DetectionOverlay(overlay, state.search.target)
            Column(Modifier.align(Alignment.TopStart).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                StatusBadge(if(state.simulation) "SIMULATION" else when {
                    state.engine.loading -> "MODEL LOADING"
                    state.engine.error != null -> "MODEL UNAVAILABLE"
                    state.search.status==SearchStatus.FOUND -> "TARGET FOUND"
                    state.search.status==SearchStatus.SEARCHING -> "SCANNING"
                    else -> "LIVE VISION"
                }, if(state.search.status==SearchStatus.FOUND) Mint else Cyan)
                state.search.target?.let { StatusBadge("TARGET / ${it.label.uppercase()}") }
            }
            if (state.cameraPermission && state.cameraStatus !in listOf("ACTIVE","IDLE")) {
                Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp),color=Background.copy(alpha=.9f),shape=RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) { Text(state.cameraStatus,color=Rose);TextButton(onClick={vm.dispatch(VisionIntent.ReloadEngine)}) { Text("다시 시도") } }
                }
            }
        }
        Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                MetricTile("INFERENCE",if(state.simulation) "simulated" else metrics.p50?.let{"%.0f ms".format(it)} ?: "--")
                MetricTile("RESULT RATE",if(state.simulation) "--" else metrics.resultHz?.let{"%.1f Hz".format(it)} ?: "--")
                MetricTile("FRAMES",if(state.simulation) "--" else metrics.processed.toString())
            }
            ConsoleCard {
                Eyebrow("VOICE COMMAND",Cyan)
                Text(state.transcript.ifBlank { "‘컵 찾아줘’라고 말해 보세요" },style=MaterialTheme.typography.titleMedium,color=if(state.transcript.isBlank()) Muted else Ink)
                Text(when(state.search.status) {
                    SearchStatus.SEARCHING -> "${state.search.target?.korean} 검색 중 · 3/5 프레임 안정화 대기"
                    SearchStatus.FOUND -> "대상을 발견했습니다"
                    SearchStatus.NOT_FOUND -> "아직 찾지 못했습니다. 방향을 바꾸고 다시 시도하세요."
                    SearchStatus.STOPPED -> "검색 중지됨"
                    SearchStatus.IDLE -> "음성 명령 또는 아래에서 대상을 선택하세요."
                },color=if(state.search.status==SearchStatus.FOUND) Mint else Muted)
                Button(onClick={ when {
                    !state.microphonePermission && !state.simulation -> requestMicrophone()
                    state.listening==ListeningStatus.RECORDING -> vm.dispatch(VisionIntent.StopListening)
                    else -> vm.dispatch(VisionIntent.StartListening)
                } },enabled=state.listening !in listOf(ListeningStatus.PREPARING,ListeningStatus.TRANSCRIBING),modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {
                    Icon(if(state.listening==ListeningStatus.RECORDING) Icons.Outlined.StopCircle else Icons.Outlined.Mic,null)
                    Spacer(Modifier.width(12.dp))
                    Text(when(state.listening) {
                        ListeningStatus.PREPARING -> "음성 모델 준비 중…"
                        ListeningStatus.RECORDING -> "녹음 종료 · 최대 8초"
                        ListeningStatus.TRANSCRIBING -> "한국어 인식 중…"
                        ListeningStatus.IDLE -> "말하기 · PUSH TO TALK"
                    })
                }
                if(state.listening!=ListeningStatus.IDLE) TextButton(onClick={vm.dispatch(VisionIntent.CancelListening)}) { Text("음성 입력 취소") }
                if(!state.microphonePermission) TextButton(onClick=openSettings) { Text("마이크 권한 설정 / 수동 대상 선택 가능") }
                state.message?.let { Text(it,color=Amber) }
            }
            state.engine.error?.let { ErrorPanel(it,vm::dispatch) }
            TargetChips(state,vm::dispatch)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={vm.dispatch(VisionIntent.StopSearch)},modifier=Modifier.weight(1f)) { Text("검색 중지") }
                OutlinedButton(onClick={vm.dispatch(VisionIntent.RetrySearch)},enabled=state.search.target!=null,modifier=Modifier.weight(1f)) { Text("다시 찾기") }
            }
            TextButton(onClick={vm.dispatch(VisionIntent.ClearTarget)}) { Text("대상 초기화") }
            Eyebrow(if(state.simulation) "FIXTURE / NO REAL INFERENCE" else state.engine.backend)
            Spacer(Modifier.height(8.dp))
        }
    }
    }
}
@Composable private fun MetricTile(label: String,value: String) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { Eyebrow(label);Text(value,style=MaterialTheme.typography.titleMedium) }
}
@Composable private fun CameraPreview(vm: VisionViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { scaleType=PreviewView.ScaleType.FILL_CENTER;implementationMode=PreviewView.ImplementationMode.COMPATIBLE } }
    var retry by remember { mutableIntStateOf(0) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.engine.ready) { if(state.engine.ready) retry++ }
    AndroidView(factory={view},modifier=Modifier.fillMaxSize())
    DisposableEffect(owner,view,retry) {
        val session = CameraSession(context,vm.controller)
        view.doOnLayout { session.bind(owner,view) { vm.dispatch(VisionIntent.CameraStatus(it)) } }
        onDispose { session.close() }
    }
}
@Composable fun DetectionOverlay(overlay: OverlayState, target: Target?) {
    Canvas(Modifier.fillMaxSize()) {
        val frame=overlay.frame ?: return@Canvas
        frame.detections.forEach { detection ->
            val box=OverlayCoordinateMapper.fillCenter(detection.box,frame.width,frame.height,size.width,size.height)
            if(box.right<=box.left || box.bottom<=box.top) return@forEach
            val color=if(detection.label==target?.label) Mint else Cyan
            drawRect(color,Offset(box.left,box.top),Size(box.right-box.left,box.bottom-box.top),style=Stroke(1.5.dp.toPx()))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color.toArgb();textSize=12.dp.toPx();typeface=android.graphics.Typeface.MONOSPACE }
            val label="${detection.label.uppercase()}  ${"%.0f".format(detection.score*100)}%"
            drawContext.canvas.nativeCanvas.drawText(label,box.left.coerceAtLeast(4.dp.toPx()),(box.top-6.dp.toPx()).coerceAtLeast(16.dp.toPx()),paint)
        }
    }
}
