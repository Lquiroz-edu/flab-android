package com.lquiroz.flab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import com.lquiroz.flab.fold.rememberHingeAngle
import com.lquiroz.flab.fold.rememberFoldSnapshot
import com.lquiroz.flab.ui.home.FLabHomeScreen
import com.lquiroz.flab.ui.theme.FLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FLabTheme {
                val foldSnapshot by rememberFoldSnapshot()
                val hingeAngle by rememberHingeAngle()
                FLabHomeScreen(foldSnapshot = foldSnapshot, hingeAngle = hingeAngle)
            }
        }
    }
}
