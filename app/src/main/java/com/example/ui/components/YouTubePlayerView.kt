package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    modifier: Modifier = Modifier,
    videoUrl: String = ""
) {
    val context = LocalContext.current
    val finalVideoUrl = videoUrl.ifBlank { "https://www.youtube.com/watch?v=$videoId" }
    
    // Determine if the video is YouTube or a direct video stream
    val isYouTube = remember(videoId, finalVideoUrl) {
        videoId.isNotBlank() || 
        finalVideoUrl.contains("youtube.com", ignoreCase = true) || 
        finalVideoUrl.contains("youtu.be", ignoreCase = true)
    }

    var webView: WebView? by remember { mutableStateOf(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(80f) } // 0f to 100f
    var isReady by remember { mutableStateOf(false) }

    // ExoPlayer Instance for direct video URLs
    var exoPlayer: ExoPlayer? by remember { mutableStateOf(null) }

    if (!isYouTube) {
        DisposableEffect(finalVideoUrl) {
            val player = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(finalVideoUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = false
                this.volume = volume / 100f
            }

            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                    isPlaying = isPlayingChanged
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isReady = (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
                }
            }
            player.addListener(listener)
            exoPlayer = player

            onDispose {
                player.removeListener(listener)
                player.release()
                exoPlayer = null
            }
        }
    }

    val htmlData = remember(videoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000; overflow: hidden; }
                #player { width: 100%; height: 100%; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script>
                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                var player;
                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        height: '100%',
                        width: '100%',
                        videoId: '$videoId',
                        playerVars: {
                            'playsinline': 1,
                            'controls': 1, // Keep iframe controls enabled for convenience
                            'rel': 0,
                            'showinfo': 0,
                            'modestbranding': 1
                        },
                        events: {
                            'onReady': onPlayerReady,
                            'onStateChange': onPlayerStateChange
                        }
                    });
                }

                function onPlayerReady(event) {
                    window.AndroidBridge.onPlayerReady();
                }

                function onPlayerStateChange(event) {
                    window.AndroidBridge.onPlayerStateChange(event.data);
                }

                function playVideo() {
                    if (player && player.playVideo) player.playVideo();
                }

                function pauseVideo() {
                    if (player && player.pauseVideo) player.pauseVideo();
                }

                function seekBy(seconds) {
                    if (player && player.getCurrentTime && player.seekTo) {
                        var currentTime = player.getCurrentTime();
                        player.seekTo(currentTime + seconds, true);
                    }
                }

                function setVolume(level) {
                    if (player && player.setVolume) player.setVolume(level);
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    class WebAppInterface(private val getWebView: () -> WebView?) {
        @JavascriptInterface
        fun onPlayerReady() {
            isReady = true
            val view = getWebView()
            view?.post {
                view.evaluateJavascript("setVolume($volume);", null)
            }
        }

        @JavascriptInterface
        fun onPlayerStateChange(state: Int) {
            isPlaying = (state == 1)
        }
    }

    LaunchedEffect(videoId, webView) {
        if (isYouTube) {
            webView?.let { view ->
                isReady = false
                isPlaying = false
                view.loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "utf-8", null)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Embed Player Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            if (isYouTube) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowContentAccess = true
                                allowFileAccess = true
                                loadsImagesAutomatically = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                            }
                            
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            
                            addJavascriptInterface(WebAppInterface { webView }, "AndroidBridge")
                            loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "utf-8", null)
                            webView = this
                        }
                    },
                    update = { view ->
                        webView = view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                exoPlayer?.let { playerInstance ->
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = playerInstance
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Seek Back Button
            IconButton(
                onClick = {
                    if (isYouTube) {
                        webView?.evaluateJavascript("seekBy(-10);", null)
                    } else {
                        exoPlayer?.let {
                            val targetPos = (it.currentPosition - 10000).coerceAtLeast(0)
                            it.seekTo(targetPos)
                        }
                    }
                },
                enabled = isReady,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Rewind 10 seconds",
                    tint = if (isReady) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Play/Pause Button
            Button(
                onClick = {
                    if (isYouTube) {
                        if (isPlaying) {
                            webView?.evaluateJavascript("pauseVideo();", null)
                            isPlaying = false
                        } else {
                            webView?.evaluateJavascript("playVideo();", null)
                            isPlaying = true
                        }
                    } else {
                        exoPlayer?.let {
                            if (it.isPlaying) {
                                it.pause()
                            } else {
                                it.play()
                            }
                        }
                    }
                },
                enabled = isReady,
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Play")
            }

            // Seek Forward Button
            IconButton(
                onClick = {
                    if (isYouTube) {
                        webView?.evaluateJavascript("seekBy(10);", null)
                    } else {
                        exoPlayer?.let {
                            val targetPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                            it.seekTo(targetPos)
                        }
                    }
                },
                enabled = isReady,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Forward 10 seconds",
                    tint = if (isReady) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Volume Controller
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Slider(
                value = volume,
                onValueChange = { newVolume ->
                    volume = newVolume
                    if (isYouTube) {
                        webView?.evaluateJavascript("setVolume(${newVolume.toInt()});", null)
                    } else {
                        exoPlayer?.let {
                            it.volume = newVolume / 100f
                        }
                    }
                },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${volume.toInt()}%",
                style = MaterialTheme.styleScheme.bodyMedium,
                modifier = Modifier
                    .width(44.dp)
                    .padding(start = 12.dp)
            )
        }
    }
}

// Support bodyMedium mapping for MaterialTheme style safely
private val MaterialTheme.styleScheme: Typography
    @Composable
    get() = typography
