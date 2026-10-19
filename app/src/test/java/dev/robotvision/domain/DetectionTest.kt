package dev.robotvision.domain
import org.junit.Assert.*
import org.junit.Test
class DetectionTest {
    private val cup=Targets.byLabel("cup")!!
    private fun frame(t:Long,label:String="cup",score:Float=.9f)=DetectionFrame(t,640,480,listOf(Detection(label,score,Box(.1f,.2f,.4f,.6f))),"test")
    @Test fun wrongClassNeverFound() { val s=StabilizeDetection();repeat(5) { assertNull(s.accept(frame(it.toLong(),"bottle"),cup,.5f)) } }
    @Test fun belowThresholdNeverFound() { val s=StabilizeDetection();repeat(5) { assertNull(s.accept(frame(it.toLong(),score=.49f),cup,.5f)) } }
    @Test fun threeInFive() { val s=StabilizeDetection();assertNull(s.accept(frame(1),cup,.5f));assertNull(s.accept(frame(2,"bottle"),cup,.5f));assertNull(s.accept(frame(3),cup,.5f));assertNull(s.accept(frame(4,"bottle"),cup,.5f));assertNotNull(s.accept(frame(5),cup,.5f)) }
    @Test fun singleTransient() { val s=StabilizeDetection();s.accept(frame(1),cup,.5f);repeat(5){assertNull(s.accept(frame(it+2L,"bottle"),cup,.5f))} }
    @Test fun duplicateAndOutOfOrder() { val s=StabilizeDetection();repeat(5){assertNull(s.accept(frame(20),cup,.5f))};assertNull(s.accept(frame(19),cup,.5f));assertNull(s.accept(frame(21),cup,.5f));assertNotNull(s.accept(frame(22),cup,.5f)) }
    @Test fun slowResultsRemainFrameBased() { val s=StabilizeDetection();s.accept(frame(1),cup,.5f);s.accept(frame(2),cup,.5f);assertNotNull(s.accept(frame(3000),cup,.5f)) }
    @Test fun resetClearsEvidence() { val s=StabilizeDetection();s.accept(frame(1),cup,.5f);s.accept(frame(2),cup,.5f);s.reset();assertNull(s.accept(frame(3),cup,.5f)) }
    @Test fun cooldown() { val c=EventCooldown();assertTrue(c.allow("cup",1));assertFalse(c.allow("cup",4999));assertTrue(c.allow("cup",5001));assertTrue(c.allow("bottle",2)) }
    @Test fun reducerStartAndTimeout() { val s=VisionReducer.reduce(SearchState(),SearchAction.Start(cup,10));assertEquals(cup,s.target);assertEquals(SearchStatus.SEARCHING,s.status);assertEquals(SearchStatus.NOT_FOUND,VisionReducer.reduce(s,SearchAction.Timeout).status) }
    @Test fun stoppedCannotBecomeFound() { val s=VisionReducer.reduce(SearchState(cup,SearchStatus.SEARCHING),SearchAction.Stop);assertEquals(s,VisionReducer.reduce(s,SearchAction.Found)) }
    @Test fun simulatedMetricsExcluded() { val m=PerformanceMetrics();m.record(frame(1).copy(simulated=true,inferenceMs=999.0));assertEquals(0L,m.value.processed);assertNull(m.value.p50) }
    @Test fun percentilesAndRate() { val m=PerformanceMetrics();(1..20).forEach { m.record(frame(it*100L).copy(inferenceMs=it.toDouble())) };assertEquals(10.0,m.value.p50!!,.001);assertEquals(19.0,m.value.p95!!,.001);assertEquals(10.0,m.value.resultHz!!,.001) }
}
