package com.moodmix.app

import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.moodmix.app.navigation.MoodMixNavigation
import com.moodmix.app.ui.theme.MoodMixTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MoodMixTheme {

                var showSplash by remember {
                    mutableStateOf(true)
                }

                if (showSplash) {
                    MoodMixSplashScreen(
                        onFinished = {
                            showSplash = false
                        }
                    )
                } else {
                    MoodMixNavigation()
                }
            }
        }
    }
}

@Composable
fun MoodMixSplashScreen(
    onFinished: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B14))
    ) {

        AndroidView(
            factory = { context ->

                VideoView(context).apply {

                    val videoUri = Uri.parse(
                        "android.resource://${context.packageName}/${R.raw.intro_anim}"
                    )

                    setVideoURI(videoUri)

                    setOnCompletionListener {
                        onFinished()
                    }

                    start()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}