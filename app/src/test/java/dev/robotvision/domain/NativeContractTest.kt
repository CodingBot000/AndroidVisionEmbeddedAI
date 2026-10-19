package dev.robotvision.domain
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
class NativeContractTest {
    @Test fun staticCapacitiesAccepted() { EfficientDetOutputParser.requireCapacity(307200,listOf(100,25,25,1)) }
    @Test(expected=IllegalArgumentException::class) fun dynamicScalarOutputsRejectedBeforeInference() { EfficientDetOutputParser.requireCapacity(307200,listOf(1,1,1,1)) }
    @Test(expected=IllegalArgumentException::class) fun wrongInputCapacityRejected() { EfficientDetOutputParser.requireCapacity(1,listOf(100,25,25,1)) }
    @Test fun outputCoordinatesAndClasses() {
        val d=EfficientDetOutputParser.parse(floatArrayOf(.2f,.1f,.6f,.4f),floatArrayOf(0f),floatArrayOf(.8f),1f,listOf("cup")).single()
        assertEquals("cup",d.label);assertEquals(Box(.1f,.2f,.4f,.6f),d.box)
    }
    @Test fun countLimitsPaddedRows() {assertTrue(EfficientDetOutputParser.parse(floatArrayOf(0f,0f,1f,1f),floatArrayOf(0f),floatArrayOf(.8f),0f,listOf("cup")).isEmpty())}
    @Test fun invalidScoresAndBoxesRejected() {assertTrue(EfficientDetOutputParser.parse(floatArrayOf(Float.NaN,0f,1f,1f),floatArrayOf(0f),floatArrayOf(.8f),1f,listOf("cup")).isEmpty())}
    @Test(expected=IllegalArgumentException::class) fun wrongOutputContractFails() {EfficientDetOutputParser.parse(floatArrayOf(1f),floatArrayOf(0f),floatArrayOf(.8f),1f,listOf("cup"))}
    @Test fun frameClosedOnceOnSuccess() {var n=0;assertEquals(42,withOwnedFrame({n++}){42});assertEquals(1,n)}
    @Test fun frameClosedOnceOnException() {var n=0;runCatching{withOwnedFrame({n++}){error("conversion")}};assertEquals(1,n)}
    @Test fun frameClosedOnceOnCancellation() {var n=0;runCatching{withOwnedFrame({n++}){throw CancellationException("background")}};assertEquals(1,n)}
}
