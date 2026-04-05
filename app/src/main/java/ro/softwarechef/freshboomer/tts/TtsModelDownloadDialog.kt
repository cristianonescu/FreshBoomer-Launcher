package ro.softwarechef.freshboomer.tts

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import ro.softwarechef.freshboomer.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TAG = "TtsDownloadDialog"

enum class DownloadState {
    CHECKING,
    DOWNLOADING,
    DONE,
    ERROR
}

/**
 * Inline footer composable that replaces the copyright text while
 * the TTS model is being checked/downloaded. Non-blocking — the app
 * is fully usable while this runs in the background.
 */
@Composable
fun TtsStatusFooter(
    onComplete: (PiperVoice) -> Unit,
    copyrightText: String,
    onSettingsClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(DownloadState.CHECKING) }
    var progress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableFloatStateOf(0f) }
    var totalMb by remember { mutableFloatStateOf(0f) }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryCount) {
        state = DownloadState.CHECKING
        progress = 0f
        downloadedMb = 0f
        totalMb = 0f
        val selectedEngine = ro.softwarechef.freshboomer.data.TtsPreference.getEngine(context)
        val voice = when (selectedEngine) {
            ro.softwarechef.freshboomer.data.TtsEngine.PIPER_SANDA -> PiperVoice.SANDA
            else -> PiperVoice.LILI
        }

        TtsModelManager.ensureMetadataInjected(context, voice)
        val modelExists = TtsModelManager.isModelDownloaded(context, voice)

        if (modelExists) {
            state = DownloadState.CHECKING
            val updateAvailable = TtsModelManager.isUpdateAvailable(context, voice)
            if (!updateAvailable) {
                Log.d(TAG, "${voice.name} model is up-to-date")
                state = DownloadState.DONE
                onComplete(voice)
                return@LaunchedEffect
            }
            Log.d(TAG, "${voice.name} model update available, downloading...")
        } else {
            Log.d(TAG, "${voice.name} model not found, downloading...")
        }

        state = DownloadState.DOWNLOADING
        val success = TtsModelManager.downloadModel(context, voice) { downloaded, total ->
            downloadedMb = downloaded / (1024f * 1024f)
            if (total > 0) {
                totalMb = total / (1024f * 1024f)
                progress = downloaded.toFloat() / total.toFloat()
            }
        }

        if (success) {
            state = DownloadState.DONE
            onComplete(voice)
        } else {
            state = DownloadState.ERROR
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            DownloadState.CHECKING -> {
                Text(
                    text = stringResource(R.string.tts_download_checking),
                    fontSize = 8.sp,
                    color = Color.Gray
                )
            }
            DownloadState.DOWNLOADING -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { if (totalMb > 0) progress else 0f },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(3.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (totalMb > 0)
                            stringResource(R.string.tts_download_progress, downloadedMb, totalMb)
                        else
                            stringResource(R.string.tts_download_progress_unknown, downloadedMb),
                        fontSize = 8.sp,
                        color = Color.Gray
                    )
                }
            }
            DownloadState.ERROR -> {
                if (onSettingsClick != null) {
                    var tapCount by remember { mutableIntStateOf(0) }
                    var firstTapTime by remember { mutableLongStateOf(0L) }

                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                val now = System.currentTimeMillis()
                                if (now - firstTapTime > 3000L) {
                                    tapCount = 1
                                    firstTapTime = now
                                } else {
                                    tapCount++
                                }
                                if (tapCount >= 5) {
                                    tapCount = 0
                                    onSettingsClick()
                                }
                            },
                        tint = Color.Gray.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = stringResource(R.string.tts_download_error_retry),
                    fontSize = 8.sp,
                    color = Color.Red,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { retryCount++ },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            DownloadState.DONE -> {
                if (onSettingsClick != null) {
                    var tapCount by remember { mutableIntStateOf(0) }
                    var firstTapTime by remember { mutableLongStateOf(0L) }

                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                val now = System.currentTimeMillis()
                                if (now - firstTapTime > 3000L) {
                                    tapCount = 1
                                    firstTapTime = now
                                } else {
                                    tapCount++
                                }
                                if (tapCount >= 5) {
                                    tapCount = 0
                                    onSettingsClick()
                                }
                            },
                        tint = Color.Gray.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = copyrightText,
                    fontSize = 8.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
