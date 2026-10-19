package dev.robotvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import dagger.hilt.android.AndroidEntryPoint
import dev.robotvision.presentation.design.RobotTheme
import dev.robotvision.presentation.navigation.RobotApp

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { RobotTheme { RobotApp() } }
    }
}
