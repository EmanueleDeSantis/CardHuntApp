package com.cardhunt.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits the device azimuth in degrees (-180..180) from the rotation-vector sensor.
 * Arrow rotation for an up-pointing icon = (bearing to target) - azimuth.
 */
@Singleton
class TargetCompass @Inject constructor(@ApplicationContext ctx: Context) {

    private val sensorManager = ctx.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun azimuthFlow(): Flow<Float> = callbackFlow {
        if (sensor == null) { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, e.values)
                SensorManager.getOrientation(rotMatrix, orientationAngles)
                trySend(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}