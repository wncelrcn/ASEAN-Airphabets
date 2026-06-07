package com.example.airphabets.presentation.screens.mode

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.airphabets.ml.ClassifierLoadResult
import com.example.airphabets.ml.ModelLoader
import com.example.airphabets.sensors.SensorSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun PracticeMode() {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var prediction by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Press Start to Practice") }

    val coroutineScope = rememberCoroutineScope()
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    // Load the model using ModelLoader which defaults to model.tflite
    val loadResult = remember { ModelLoader.loadDefault(context) }
    val classifier = (loadResult as? ClassifierLoadResult.Success)?.classifier

    DisposableEffect(Unit) {
        onDispose {
            classifier?.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (countdown > 0) {
            Text(text = "Starting in $countdown", style = MaterialTheme.typography.display1)
        } else if (isRecording) {
            Text(text = "Recording...", style = MaterialTheme.typography.title2)
        } else {
            Text(text = message, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center)
            if (prediction.isNotEmpty()) {
                Text(text = prediction, style = MaterialTheme.typography.display2)
            }

            if (classifier == null) {
                val errorMsg = (loadResult as? ClassifierLoadResult.Error)?.message ?: "Loading..."
                Text(text = errorMsg, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption1)
            } else {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            prediction = ""
                            message = "Get Ready..."
                            for (i in 3 downTo 1) {
                                countdown = i
                                delay(1000)
                            }
                            countdown = 0
                            message = "Recording..."
                            isRecording = true

                            // Record for 3 seconds
                            val sensorData = recordSensorData(sensorManager, 3000)
                            isRecording = false
                            message = "Processing..."

                            val result = classifier.classify(sensorData)
                            prediction = result.toString()
                            message = "Done"
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Start")
                }
            }
        }
    }
}

suspend fun recordSensorData(sensorManager: SensorManager, durationMs: Long): List<SensorSample> {
    return suspendCancellableCoroutine { continuation ->
        val data = mutableListOf<SensorSample>()

        // Arrays to hold latest sensor values
        val accData = FloatArray(3)
        val gyroData = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(it.values, 0, accData, 0, 3)
                    } else if (it.sensor.type == Sensor.TYPE_GYROSCOPE) {
                        System.arraycopy(it.values, 0, gyroData, 0, 3)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // SENSOR_DELAY_GAME is approx 20ms (50Hz), SENSOR_DELAY_FASTEST is as fast as possible (often 100Hz+)
        if (accSensor != null) {
            sensorManager.registerListener(listener, accSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (gyroSensor != null) {
            sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }

        // Sampling loop at 100Hz
        val sampleRate = 100
        val sampleInterval = 1000L / sampleRate // 10ms
        val samplesNeeded = ((durationMs / 1000.0) * sampleRate).toInt()

        val timer = java.util.Timer()
        var samplesTaken = 0

        timer.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                if (samplesTaken >= samplesNeeded) {
                    timer.cancel()
                    sensorManager.unregisterListener(listener)
                    if (continuation.isActive) {
                        continuation.resume(data)
                    }
                    return
                }
                // Snapshot current values
                val sample = SensorSample(
                    timestampNanos = System.nanoTime(),
                    ax = accData[0],
                    ay = accData[1],
                    az = accData[2],
                    gx = gyroData[0],
                    gy = gyroData[1],
                    gz = gyroData[2]
                )

                data.add(sample)
                samplesTaken++
            }
        }, 0, sampleInterval)

        continuation.invokeOnCancellation {
            timer.cancel()
            sensorManager.unregisterListener(listener)
        }
    }
}
