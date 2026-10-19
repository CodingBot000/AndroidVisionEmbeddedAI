package dev.robotvision.presentation
import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import dev.robotvision.data.modelassets.ModelAssetManager
import dev.robotvision.data.vision.*
import dev.robotvision.domain.*
import dev.robotvision.presentation.vision.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
@OptIn(ExperimentalCoroutinesApi::class)
class VisionViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private lateinit var vm:VisionViewModel
    private lateinit var store:ViewModelStore
    private lateinit var audio:AudioInput
    private lateinit var speech:SpeechRecognizerEngine
    private lateinit var controller:VisionController
    private val frames=MutableSharedFlow<DetectionFrame>()
    private val events=MutableStateFlow<List<Event>>(emptyList())
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        controller=mockk(relaxed=true)
        every { controller.state } returns MutableStateFlow(EngineState(ready=true,backend="test CPU"))
        every { controller.metrics } returns MutableStateFlow(Metrics())
        every { controller.frames } returns frames.asSharedFlow()
        every { controller.performance } returns PerformanceMetrics()
        audio=mockk(relaxed=true);speech=mockk(relaxed=true)
        val repository=object:EventRepository { override val events=this@VisionViewModelTest.events
            override suspend fun save(event:Event) { events.value=events.value+event }
            override suspend fun clear() { events.value=emptyList() } }
        val settings=object:SettingsRepository {override val settings=MutableStateFlow(Settings());override suspend fun setConfidence(value:Float) {};override suspend fun setHaptic(value:Boolean) {} }
        val assets=mockk<ModelAssetManager>();every {assets.description()} returns "test";every {assets.hashSummary()} returns "hash"
        val clock=object:Clock {override fun now()=dispatcher.scheduler.currentTime;override fun wallTime()=1000L+now()}
        vm=VisionViewModel(controller,speech,audio,repository,settings,clock,SavedStateHandle(),assets,dispatcher)
        store=ViewModelStore();store.put("vision",vm)
    }
    private fun runMvi(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { store.clear(); runCurrent() }
    }
    @After fun cleanup() { store.clear();dispatcher.scheduler.runCurrent();Dispatchers.resetMain() }
    @Test fun stateFlowPublishesSearchTransitions()=runMvi {
        vm.state.map {it.search.status}.distinctUntilChanged().test {
            assertEquals(SearchStatus.IDLE,awaitItem())
            vm.dispatch(VisionIntent.SelectTarget("cup"));runCurrent()
            assertEquals(SearchStatus.SEARCHING,awaitItem())
            vm.dispatch(VisionIntent.StopSearch);runCurrent()
            assertEquals(SearchStatus.STOPPED,awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test fun intentFailureDoesNotKillSubsequentCommands()=runMvi {
        coEvery { controller.unload() } throws IllegalStateException("native close")
        vm.dispatch(VisionIntent.ReloadEngine);runCurrent()
        assertTrue(vm.state.value.message!!.contains("native close"))
        vm.dispatch(VisionIntent.SelectTarget("cup"));runCurrent()
        assertEquals(SearchStatus.SEARCHING,vm.state.value.search.status)
    }
    @Test fun selectionStartsSearch()=runMvi { vm.dispatch(VisionIntent.SelectTarget("cup"));runCurrent();assertEquals("cup",vm.state.value.search.target?.label);assertEquals(SearchStatus.SEARCHING,vm.state.value.search.status) }
    @Test fun searchTimesOut()=runMvi { vm.dispatch(VisionIntent.SelectTarget("cup"));runCurrent();advanceTimeBy(20_001);runCurrent();assertEquals(SearchStatus.NOT_FOUND,vm.state.value.search.status);assertEquals("NOT_FOUND",events.value.single().type) }
    @Test fun stableFramesSaveOneEvent()=runMvi {
        vm.dispatch(VisionIntent.SelectTarget("cup"));runCurrent()
        repeat(6) { frames.emit(DetectionFrame(it.toLong(),640,480,listOf(Detection("cup",.9f,Box(0f,0f,1f,1f))),"test CPU",12.0));runCurrent() }
        assertEquals(SearchStatus.FOUND,vm.state.value.search.status);assertEquals(1,events.value.count{it.type=="FOUND"})
    }
    @Test fun speechErrorKeepsCamera()=runMvi {
        coEvery { speech.load() } throws IllegalStateException("model error")
        vm.dispatch(VisionIntent.Permissions(true,true));vm.dispatch(VisionIntent.CameraStatus("ACTIVE"));vm.dispatch(VisionIntent.StartListening);runCurrent()
        assertEquals("ACTIVE",vm.state.value.cameraStatus);assertEquals("model error",vm.state.value.message);assertEquals(ListeningStatus.IDLE,vm.state.value.listening)
    }
    @Test fun backgroundCleansAudioAndStopsSearch()=runMvi {
        vm.dispatch(VisionIntent.SelectTarget("cup"));vm.dispatch(VisionIntent.Background);runCurrent()
        verify {audio.cancel()};coVerify {controller.unload()};assertEquals(SearchStatus.STOPPED,vm.state.value.search.status);assertNull(vm.overlay.value.frame)
    }
    @Test fun microphoneCommandStartsSearch()=runMvi {
        coEvery {audio.capture(any())} coAnswers {firstArg<()->Unit>().invoke();FloatArray(4000){.1f}}
        coEvery {speech.transcribe(any())} returns "컵 찾아줘"
        vm.dispatch(VisionIntent.Permissions(true,true));vm.dispatch(VisionIntent.StartListening);runCurrent()
        assertEquals("cup",vm.state.value.search.target?.label);assertEquals("컵 찾아줘",vm.state.value.transcript)
    }
    @Test fun staleBoxesDisappear()=runMvi { runCurrent();frames.emit(DetectionFrame(0,640,480,emptyList(),"test"));runCurrent();assertNotNull(vm.overlay.value.frame);advanceTimeBy(1001);runCurrent();assertNull(vm.overlay.value.frame) }
}
