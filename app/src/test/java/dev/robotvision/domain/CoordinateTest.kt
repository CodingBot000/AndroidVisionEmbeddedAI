package dev.robotvision.domain
import org.junit.Assert.*
import org.junit.Test
class CoordinateTest {
    val box=Box(.1f,.2f,.4f,.6f)
    private fun checkBox(expected:Box,actual:Box) { assertEquals(expected.left,actual.left,.0001f);assertEquals(expected.top,actual.top,.0001f);assertEquals(expected.right,actual.right,.0001f);assertEquals(expected.bottom,actual.bottom,.0001f) }
    @Test fun zeroRotation() { checkBox(box,OverlayCoordinateMapper.rotate(box,0)) }
    @Test fun rotate90() { checkBox(Box(.4f,.1f,.8f,.4f),OverlayCoordinateMapper.rotate(box,90)) }
    @Test fun rotate180() { checkBox(Box(.6f,.4f,.9f,.8f),OverlayCoordinateMapper.rotate(box,180)) }
    @Test fun rotate270() { checkBox(Box(.2f,.6f,.6f,.9f),OverlayCoordinateMapper.rotate(box,270)) }
    @Test fun fourTurnsIdentity() { var b=box;repeat(4) { b=OverlayCoordinateMapper.rotate(b,90) };checkBox(box,b) }
    @Test fun landscapeIntoPortraitCrop() { checkBox(Box(-50f,0f,350f,300f),OverlayCoordinateMapper.fillCenter(Box(0f,0f,1f,1f),640,480,300f,300f)) }
    @Test fun sameRatio() { checkBox(Box(30f,80f,120f,240f),OverlayCoordinateMapper.fillCenter(box,600,800,300f,400f)) }
}
