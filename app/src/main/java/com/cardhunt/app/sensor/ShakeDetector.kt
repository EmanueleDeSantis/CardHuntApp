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
import kotlin.math.sqrt

@Singleton
class ShakeDetector @Inject constructor(@ApplicationContext ctx: Context) {
    
    private val sensorManager = ctx.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    fun shakeFlow(): Flow<Unit> = callbackFlow {
        if (sensor == null) { close(); return@callbackFlow }
        
        var lastShakeTime = 0L
        var lastUpdate = 0L
        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f
        var shakeCount = 0
        
        val SHAKE_THRESHOLD = 50.0f
        val SHAKE_INTERVAL = 500L
        val REQUIRED_SHAKES = 2
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - lastUpdate < 100) return
                val diffTime = currentTime - lastUpdate
                lastUpdate = currentTime
                
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val speed = sqrt(
                    ((x - lastX).toDouble() * (x - lastX) +
                     (y - lastY).toDouble() * (y - lastY) +
                     (z - lastZ).toDouble() * (z - lastZ)) / diffTime * 10000
                ).toFloat()
                
                if (speed > SHAKE_THRESHOLD) {
                    shakeCount++
                    if (shakeCount >= REQUIRED_SHAKES) {
                        if (currentTime - lastShakeTime > SHAKE_INTERVAL) {
                            lastShakeTime = currentTime
                            shakeCount = 0
                            trySend(Unit)
                        }
                    }
                } else {
                    shakeCount = 0
                }
                
                lastX = x
                lastY = y
                lastZ = z
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}