package dev.robotvision.presentation.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import dev.robotvision.debug.DebugScenario
import dev.robotvision.domain.*
import dev.robotvision.domain.Target
import dev.robotvision.presentation.design.*
import dev.robotvision.presentation.vision.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Destination(val route: String, val title: String, val icon: ImageVector)
private val destinations = listOf(Destination("dashboard", "홈", Icons.Outlined.Dashboard),
    Destination("vision", "비전", Icons.Outlined.CenterFocusStrong), Destination("history", "기록", Icons.Outlined.History),
    Destination("diagnostics", "진단", Icons.Outlined.MonitorHeart), Destination("settings", "설정", Icons.Outlined.Tune))

@Composable fun RobotApp(vm: VisionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val metrics by vm.metrics.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "dashboard"
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    fun refreshPermissions() { vm.dispatch(VisionIntent.Permissions(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { refreshPermissions(); vm.dispatch(VisionIntent.Foreground) }
            if (event == Lifecycle.Event.ON_STOP) vm.dispatch(VisionIntent.Background)
        }
        owner.lifecycle.addObserver(observer); refreshPermissions()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { vm.effect.collect { effect -> when (effect) {
        is VisionEffect.Snackbar -> snackbar.showSnackbar(effect.message)
        VisionEffect.TargetFound -> if (vm.state.value.settings.haptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    } } }
    Scaffold(containerColor = Background, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.fillMaxWidth().background(Background).statusBarsPadding().padding(horizontal=20.dp,vertical=16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                    Text("ROBOT VISION", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                    Icon(Icons.Outlined.Sensors, contentDescription="온디바이스 AI", tint=Cyan)
                }
                Spacer(Modifier.height(5.dp)); Eyebrow("OFFLINE PERCEPTION CONSOLE  /  01")
                if (state.simulation) { Spacer(Modifier.height(10.dp)); StatusBadge("SIMULATION / FAKE DATA", Amber) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor=Panel) { destinations.forEach { item ->
                NavigationBarItem(selected=route==item.route, onClick={ nav.navigate(item.route) {
                    popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true
                } }, icon={ Icon(item.icon, contentDescription=item.title) }, label={ Text(item.title) },
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=Cyan, indicatorColor=Elevated))
            } }
        }) { padding ->
        NavHost(navController=nav, startDestination="dashboard", modifier=Modifier.padding(padding)) {
            composable("dashboard") { DashboardScreen(state, history.take(3), { nav.navigate("vision") }, vm::dispatch) }
            composable("vision") {
                LiveVisionScreen(vm, state, metrics,
                    requestCamera={ cameraPermission.launch(Manifest.permission.CAMERA) },
                    requestMicrophone={ microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                    openSettings={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) })
            }
            composable("history") { HistoryScreen(history) { vm.dispatch(VisionIntent.ClearHistory) } }
            composable("diagnostics") { DiagnosticsScreen(state, metrics, vm::dispatch) }
            composable("settings") { SettingsScreen(state, vm::dispatch) }
        }
    }
}
@Composable fun TargetChips(state: VisionUiState, dispatch: (VisionIntent) -> Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Targets.all.chunked(4).forEach { targets ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                targets.forEach { target ->
                    FilterChip(selected=state.search.target==target,
                        onClick={ dispatch(VisionIntent.SelectTarget(target.label)) }, label={ Text(target.korean) },
                        modifier=Modifier.weight(1f).heightIn(min=48.dp))
                }
            }
        }
    }
}
@Composable fun DashboardScreen(state: VisionUiState, events: List<Event>, start: () -> Unit, dispatch: (VisionIntent) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement=Arrangement.spacedBy(20.dp)) {
        SectionTitle("Mission control", "주변을 인식하고, 음성으로 대상을 찾으세요.")
        ConsoleCard {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Eyebrow("PERCEPTION ENGINE", Cyan)
                StatusBadge(if (state.engine.ready) "ENGINE READY" else if (state.engine.loading) "LOADING" else "NOT READY",
                    if (state.engine.ready) Mint else Amber)
            }
            SensorGraphic(Modifier.fillMaxWidth().height(164.dp))
            Text("Intelligence.\nEntirely on device.", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Medium)
            Text("카메라와 마이크의 입력은 기기 안에서만 처리합니다.", color=Muted)
            HorizontalDivider(color=Elevated)
            KeyValue("VISION", state.engine.backend)
            KeyValue("SPEECH", if (state.speechReady) "sherpa-onnx / CPU" else "ON DEMAND / 한국어")
            Button(onClick=start, modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                Icon(Icons.Outlined.CenterFocusStrong,null); Spacer(Modifier.width(12.dp)); Text("START LIVE SCAN")
            }
        }
        state.engine.error?.let { ErrorPanel(it, dispatch) }
        Eyebrow("QUICK TARGETS")
        TargetChips(state) { intent -> dispatch(intent); start() }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("최근 이벤트",fontWeight=FontWeight.SemiBold); Eyebrow("LOCAL TIMELINE") }
        if (events.isEmpty()) EmptyState("아직 기록이 없습니다", "대상을 발견하면 로컬 타임라인에 표시됩니다.")
        else events.forEach { EventCard(it) }
    }
}
@Composable fun ErrorPanel(error: String, dispatch: (VisionIntent) -> Unit) {
    ConsoleCard { StatusBadge("ENGINE ATTENTION", Rose); Text(error, color=Rose)
        Text("모델 누락·손상 시 tools/fetch_models.py로 검증 후 APK를 다시 빌드하세요.",color=Muted)
        OutlinedButton(onClick={ dispatch(VisionIntent.ReloadEngine) }) { Text("엔진 다시 로드") }
    }
}
@Composable fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label, color=Muted, style=MaterialTheme.typography.bodySmall, modifier=Modifier.weight(.38f))
        Text(value, fontFamily=FontFamily.Monospace, style=MaterialTheme.typography.bodySmall, modifier=Modifier.weight(.62f))
    }
}
@Composable fun EmptyState(title: String, description: String) {
    ConsoleCard { Icon(Icons.Outlined.Radar,null,tint=Muted); Text(title); Text(description,color=Muted) }
}
@Composable fun EventCard(event: Event) {
    ConsoleCard {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            StatusBadge(event.type, if (event.type=="FOUND") Mint else Amber)
            Text(SimpleDateFormat("MM.dd HH:mm:ss", Locale.KOREA).format(Date(event.time)),color=Muted,style=MaterialTheme.typography.labelSmall)
        }
        Text(event.label?.let { Targets.byLabel(it)?.korean ?: it } ?: event.type, style=MaterialTheme.typography.titleMedium)
        if (event.simulated) StatusBadge("SIMULATION / FAKE DATA", Amber)
        KeyValue("ENGINE",event.backend)
        KeyValue("CONFIDENCE",event.score?.let { "%.1f %%".format(it*100) } ?: "--")
        KeyValue("INFERENCE",event.latencyMs?.let { "%.1f ms".format(it) } ?: "--")
        if (event.detail.isNotEmpty()) Text(event.detail,color=Muted,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun HistoryScreen(events: List<Event>, clear: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        SectionTitle("Event history", "인식 결과의 최소 메타데이터만 저장합니다.")
        OutlinedButton(onClick={ confirm=true },enabled=events.isNotEmpty()) { Icon(Icons.Outlined.DeleteOutline,null); Text("전체 기록 삭제") }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp), contentPadding=PaddingValues(bottom=20.dp)) {
            if (events.isEmpty()) item { EmptyState("기록 대기 중", "이미지와 음성 원본은 저장하지 않습니다.") }
            items(events,key={it.id}) { EventCard(it) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("기록을 삭제할까요?")},
        text={Text("로컬 이벤트 전체를 삭제합니다. 이 작업은 되돌릴 수 없습니다.")},
        confirmButton={TextButton(onClick={clear();confirm=false}) { Text("삭제") }},
        dismissButton={TextButton(onClick={confirm=false}) { Text("취소") }})
}
private fun Double?.ms() = this?.let { "%.1f ms".format(it) } ?: "--"
@Composable fun DiagnosticsScreen(state: VisionUiState, metrics: Metrics, dispatch: (VisionIntent) -> Unit) {
    val memory = remember { android.os.Debug.getPss() / 1024.0 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        SectionTitle("System health", "측정된 값과 검증 대기 항목을 구분합니다.")
        ConsoleCard {
            Eyebrow("RUNTIME / CPU BASELINE",Cyan)
            KeyValue("VISION",state.engine.backend)
            KeyValue("SPEECH","sherpa-onnx 1.13.8 / Moonshine v2 / CPU")
            KeyValue("ABI",Build.SUPPORTED_ABIS.joinToString())
            KeyValue("ACCELERATION","CPU only · GPU/NPU 미검증")
            KeyValue("CAMERA",if(state.cameraPermission) state.cameraStatus else "PERMISSION REQUIRED")
            KeyValue("AUDIO",if(state.microphonePermission) state.listening.name else "PERMISSION REQUIRED")
            state.engine.reason?.let { Text(it,color=Amber,style=MaterialTheme.typography.bodySmall) }
            state.engine.error?.let { Text(it,color=Rose) }
        }
        ConsoleCard {
            Eyebrow("OBSERVED TELEMETRY",Cyan)
            if (state.simulation) Text("시뮬레이션 값은 성능 통계에 포함하지 않습니다.",color=Amber)
            KeyValue("LOAD",metrics.modelLoadMs.ms())
            KeyValue("VISION P50",metrics.p50.ms()); KeyValue("VISION P95",metrics.p95.ms())
            KeyValue("FRAME → RESULT",metrics.totalMs.ms())
            KeyValue("RESULT RATE",metrics.resultHz?.let { "%.1f Hz".format(it) } ?: "--")
            KeyValue("PROCESSED",metrics.processed.toString());KeyValue("SKIPPED",metrics.skipped.toString())
            KeyValue("STT",metrics.speechMs.ms()); KeyValue("PROCESS PSS","%.1f MB (snapshot)".format(memory))
            Text("Skipped = 엔진 비준비·종료 후 전달된 분석 프레임. CameraX 내부 드롭 수와 카메라 FPS는 제공하지 않습니다.",color=Muted,style=MaterialTheme.typography.bodySmall)
        }
        ConsoleCard { Eyebrow("MODEL PROVENANCE"); Text(state.modelDescription,style=MaterialTheme.typography.bodySmall)
            Text(state.hashes,fontFamily=FontFamily.Monospace,color=Muted,style=MaterialTheme.typography.labelSmall) }
        OutlinedButton(onClick={dispatch(VisionIntent.ReloadEngine)}) { Text("실제 엔진 다시 로드") }
        if (DebugScenario.available) OutlinedButton(onClick={dispatch(VisionIntent.ToggleSimulation)},modifier=Modifier.fillMaxWidth()) {
            Text(if(state.simulation) "실제 엔진 모드로 전환" else "Debug 시뮬레이션 시작")
        }
        Text("DEVICE PENDING · 카메라 정렬 / 마이크 인식률 / 가속기 / 발열 / 장시간 안정성",color=Amber,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun SettingsScreen(state: VisionUiState, dispatch: (VisionIntent) -> Unit) {
    var confidence by remember(state.settings.confidence) { mutableFloatStateOf(state.settings.confidence) }
    var showLicense by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        SectionTitle("Console settings", "기기 안에 저장되는 인식 설정")
        ConsoleCard {
            Eyebrow("DETECTION CONFIDENCE",Cyan)
            Text("%.0f %%".format(confidence*100),style=MaterialTheme.typography.displaySmall,fontFamily=FontFamily.Monospace)
            Slider(value=confidence,onValueChange={confidence=it},onValueChangeFinished={dispatch(VisionIntent.SetConfidence(confidence))},valueRange=.3f.. .9f)
            Text("최근 5개 프레임 중 3개에서 임계값을 넘으면 발견으로 판정합니다.",color=Muted)
        }
        ConsoleCard {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("발견 시 진동 피드백",modifier=Modifier.weight(1f))
                Switch(checked=state.settings.haptic,onCheckedChange={dispatch(VisionIntent.SetHaptic(it))})
            }
            HorizontalDivider(color=Elevated)
            Text("검색 제한 시간 20초 · 최대 녹음 8초",color=Muted)
            Text("이벤트는 직접 삭제할 때까지 로컬에 보관합니다. 이미지·음성 원본은 저장하지 않습니다.",color=Muted)
        }
        ConsoleCard {
            Eyebrow("ABOUT / 1.0.0")
            Text("ROBOT VISION",fontWeight=FontWeight.Bold)
            Text("Powered by Moonshine AI",color=Cyan)
            Text("온디바이스 인지·명령 통합 포트폴리오. 로봇 모터 제어와 클라우드 음성 인식을 사용하지 않습니다.",color=Muted)
            Text("한국어 Moonshine 모델: Community License. 평가·비상업 사용 조건과 상업 이용 조건은 원문을 확인하세요.",color=Amber)
            TextButton(onClick={showLicense=true}) { Text("모델 라이선스 원문") }
        }
    }
    if (showLicense) {
        val license = remember { runCatching { context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } + "\n\n" + context.assets.open("models/LICENSE").bufferedReader().use { it.readText() } }.getOrDefault("LICENSES/MOONSHINE-LICENSE.txt 참조") }
        AlertDialog(onDismissRequest={showLicense=false},title={Text("Moonshine AI license")},
            text={Text(license,modifier=Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)},
            confirmButton={TextButton(onClick={showLicense=false}) { Text("닫기") }})
    }
}
