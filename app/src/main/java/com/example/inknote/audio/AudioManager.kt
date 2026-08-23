package com.example.inknote.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMillis: Long = 0L
    private var isRecording = false
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _recordingState = MutableStateFlow(false)
    val recordingState: StateFlow<Boolean> = _recordingState.asStateFlow()

    fun startRecording(notebookId: Long): Boolean {
        try {
            stopRecording()
            val audioDir = File(context.filesDir, "audio_recordings")
            if (!audioDir.exists()) audioDir.mkdirs()

            val fileName = "rec_${notebookId}_${System.currentTimeMillis()}.m4a"
            val file = File(audioDir, fileName)
            currentFile = file

            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = newRecorder
            isRecording = true
            _recordingState.value = true
            startTimeMillis = System.currentTimeMillis()

            timerJob = scope.launch {
                while (isRecording) {
                    _elapsedMillis.value = System.currentTimeMillis() - startTimeMillis
                    delay(100)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            _recordingState.value = false
            return false
        }
    }

    fun getCurrentElapsedMs(): Long {
        if (!isRecording) return 0L
        return System.currentTimeMillis() - startTimeMillis
    }

    fun stopRecording(): RecordingResult? {
        if (!isRecording) return null
        isRecording = false
        _recordingState.value = false
        timerJob?.cancel()

        try {
            recorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder?.release()
            recorder = null
        }

        val file = currentFile ?: return null
        val duration = _elapsedMillis.value
        _elapsedMillis.value = 0L

        return RecordingResult(
            fileName = file.name,
            filePath = file.absolutePath,
            durationMs = duration
        )
    }

    data class RecordingResult(
        val fileName: String,
        val filePath: String,
        val durationMs: Long
    )
}

class AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun loadAudio(filePath: String, autoPlay: Boolean = false) {
        try {
            stop()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                _duration.value = duration.toLong()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0L
                    progressJob?.cancel()
                }
            }
            if (autoPlay) {
                play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) {
            applySpeed()
            player.start()
            _isPlaying.value = true
            startProgressTracking()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                progressJob?.cancel()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            val target = positionMs.coerceIn(0, it.duration.toLong()).toInt()
            it.seekTo(target)
            _currentPosition.value = target.toLong()
        }
    }

    fun skipForward(seconds: Int = 5) {
        mediaPlayer?.let {
            val newPos = (it.currentPosition + seconds * 1000).coerceAtMost(it.duration)
            it.seekTo(newPos)
            _currentPosition.value = newPos.toLong()
        }
    }

    fun skipBackward(seconds: Int = 5) {
        mediaPlayer?.let {
            val newPos = (it.currentPosition - seconds * 1000).coerceAtLeast(0)
            it.seekTo(newPos)
            _currentPosition.value = newPos.toLong()
        }
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        applySpeed()
    }

    private fun applySpeed() {
        mediaPlayer?.let { player ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val params = player.playbackParams
                    params.speed = _playbackSpeed.value
                    player.playbackParams = params
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPosition.value = it.currentPosition.toLong()
                    }
                }
                delay(100)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        _isPlaying.value = false
        _currentPosition.value = 0L
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }
}
