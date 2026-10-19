package dev.robotvision.debug
import androidx.compose.runtime.Composable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.robotvision.presentation.design.*
@Composable fun DebugFixtureSurface() {
    Box(Modifier.fillMaxSize().background(Background)) {
        Canvas(Modifier.fillMaxSize()) {
            val step=32.dp.toPx()
            var x=0f;while(x<size.width) { drawLine(Cyan.copy(alpha=.06f),Offset(x,0f),Offset(x,size.height));x+=step }
            var y=0f;while(y<size.height) { drawLine(Cyan.copy(alpha=.06f),Offset(0f,y),Offset(size.width,y));y+=step }
        }
        Text("FIXTURE COORDINATE SPACE",Modifier.align(Alignment.BottomCenter).padding(16.dp),color=Amber,style=MaterialTheme.typography.labelSmall)
    }
}
