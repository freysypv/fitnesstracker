package com.example.fitnesstraker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitnesstraker.ui.theme.FitnesstrakerTheme

// Class 1

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.sqrt
// Class 2
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry

//class 3
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Class 4
import android.widget.Toast
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.example.fitnesstraker.data.AppDatabase
import com.example.fitnesstraker.data.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext


// call sensor automatic
@SuppressLint("SetTextI18n")
 class MainActivity : ComponentActivity(), SensorEventListener {

    // compose state - read by the fun, written from sensor callbacks
    private var motionText by mutableStateOf("motion: Stationary")
    private var gyroText by mutableStateOf("Rotation (Pitch, Roll, Yaw): 0.00, 0.00, 0.00")
    private var directionText by mutableStateOf("Direction")

    // sensor
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null

    // Sensor Fusion - both needed for the compass
    private var accelValues = FloatArray(3)
    private var magnetValues = FloatArray(3)

    // Baseline - cancel gravity
    private var baselineX = 0f
    private var baselineY = 0f
    private var baselineZ = 9.8f

    private lateinit var barChart: BarChart
    // sample data for the chart
    private val hourlyStep =floatArrayOf(1000f, 1200f,0f, 800f)

    // -- hardware step counter
    private var stepCounterSensor: Sensor? = null
    // -- step tracking
    private var sessionStepCount = 0
    private var initialStepCount = -1
    // -- health metric display state
    private var stepCountText by mutableStateOf("Steps: 0")
    private var caloriesText  by mutableStateOf("Calories: 0.0 kcal")
    private var distanceText by mutableStateOf("Distance: 0.00 km")
    // -- Demo Mode
    private var isDemoMode by mutableStateOf(false)
    private var demoHandler = Handler(Looper.getMainLooper())

    // Room
    private lateinit var  database: AppDatabase
    private var sessionHistory by mutableStateOf<List<SessionEntity>> (emptyList())

    //calibration
    private var isCalibrating = false
    private val calibrationSamples = mutableListOf<FloatArray>()

    // accuracy warning
    private var accuracyText by mutableStateOf("")


    companion object {
        private const val STATIONARY_THRESHOLD = 2.0f
        private const val WALKING_THRESHOLD = 12.0f
        private const val CALORIE_PER_STEP = 0.04f // Kcal burned per step
        private const val STRIDE_LENGTH_M = 0.762f // meters for step
        private const val ACTIVITY_PERMISSION_CODE = 100  // identifies the permission

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSensors()
        requestActivityPermission()
        database = AppDatabase.getInstance(this)
        loadHistory()

        setContent {
            FitnesstrakerTheme() {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FitnessTrackerScreen(
                        motionText = motionText,
                        gyroText = gyroText,
                        directionText = directionText,
                        onRefreshChartClick = { setupChart()},
                        onChartCreated = { chart -> barChart = chart; setupChart()},
                        stepCountText = stepCountText,
                        caloriesText = caloriesText,
                        distanceText = distanceText,
                        isDemoMode = isDemoMode,
                        onDemoModeClick = {toggleDemoMode()},
                        accuracyText = accuracyText,
                        isCalibrating = isCalibrating,
                        onCalibrateClick = { startCalibration()},
                        sessionHistory = sessionHistory,
                        onSaveSessionClick = { saveSession() }

                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepCounterSensor?.let { sensorManager.registerListener(this,it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        demoHandler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()
        sensorManager.unregisterListener(this)//
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    // Request Activity _Recognition
    private fun requestActivityPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                ACTIVITY_PERMISSION_CODE
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelValues = event.values.clone()
                handleAccelerometer(event.values)
            }

            Sensor.TYPE_GYROSCOPE -> {
                handleGyroscope(event.values)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetValues = event.values.clone() // clone prevent it from updating over and over
                updateCompass()
            }
            // ASSIGNMENT 1
            //   Add two more cases to this when block, copying the shape of the
            //   accelerometer case above:
            //   - Sensor.TYPE_GYROSCOPE: call handleGyroscope(event.values)
            //   - Sensor.TYPE_MAGNETIC_FIELD: save a CLONE of event.values into
            //     magnetValues (same reason as above), then call updateCompass()

//            Sensor.TYPE_STEP_COUNTER -> {
//                handleStepCounter(event.values[0].toINT())
//            }


        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        accuracyText = if (accuracy== SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            "${sensor.name} accuracy LOW - please recalibrate!"
        else ""
    }

    private fun handleAccelerometer(values: FloatArray) {
        val x = values[0] - baselineX
        val y = values[1] - baselineY
        val z = values[2] - baselineZ
        val magnitude = sqrt(x * x + y * y + z * z)
        motionText = "Motion: ${classifyMotion(magnitude)}"
        if (isCalibrating) {
            calibrationSamples.add(floatArrayOf(values[0], values[1], values[2]))
            if (calibrationSamples.size >= 20) finishCalibration()
        }
    }

    private fun classifyMotion(magnitude: Float): String {
        return when {
            magnitude < STATIONARY_THRESHOLD -> "Stationary"
            magnitude < WALKING_THRESHOLD -> "Walking"
            else -> "Jogging"
        }
    }

    // values [] are rotation rates in radians/second - %.2f keeps 2 decimal place
    private fun handleGyroscope(values: FloatArray) {
        gyroText = "Rotation (Pitch, Roll, Yaw): ${"%.2f".format(values[0])}, ${"%.2f".format(values[1])}, ${"%.2f".format(values[2])}"
    }

    private fun updateCompass() {
        val rm = FloatArray(9)
        val im = FloatArray(9)
        if (SensorManager.getRotationMatrix(rm, im, accelValues, magnetValues)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rm, orientation)
            val deg = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360
            directionText = "Direction: ${getDirection(deg)}"
        }
    }

    private fun getDirection(d: Float): String = when {
        d < 45 || d >= 315 -> "N" // 0-44 and 315-359
        d < 135 -> "E" // 45-134
        d < 225 -> "S" // 135-224
        else -> "W" // 225-314
    }

    private fun setupChart() {
        val entries = hourlyStep.mapIndexed {
            index,value ->
            BarEntry(index.toFloat(), value)
        }

        val dataSet = BarDataSet (entries, "Steps").apply {
            color = Color.CYAN
            // ASSIGNMENT 2
            // -- Step 1 of 2 -- Implement this!
            //   Set the size of the value labels (the numbers above each bar)
            valueTextSize = 10f
            //   to 10f.
        }

        barChart.apply {
            data = BarData(dataSet)
            // ASSIGNMENT 2 -- Step 2 of 2 -- Implement this!
            //   Set the chart's description text to "Step Count per Hour".
            description.text = "Step Count per Hour"

        }
        barChart.apply {
            data = BarData( dataSet)

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f

            axisRight.isEnabled = false

            setFitBars(true)

            animateY(800)

            invalidate()
        }
        barChart.data = BarData(dataSet)
        barChart.invalidate()

    }

    private fun handleStepCounter(totalStep: Int) {
        if (initialStepCount == -1) {
            initialStepCount = totalStep
        }
        sessionStepCount = totalStep - initialStepCount
        updateHealthMetrics()
        updateChartWithSteps()
    }

    private fun updateHealthMetrics(){
        stepCountText = "Step: $sessionStepCount"
        caloriesText = "Calories: ${"%.1f".format(calculateCalories(sessionStepCount))} kcal"
        distanceText = "Distance: ${"%.2f".format(calculateDistance(sessionStepCount))} km"
    }

    private fun calculateCalories(steps: Int): Float = steps * CALORIE_PER_STEP
    private fun calculateDistance( steps: Int): Float = (steps * STRIDE_LENGTH_M)

    private fun updateChartWithSteps() {
        val  updateSteps = floatArrayOf(
            sessionStepCount *0.3f,
            sessionStepCount *0.4f,
            sessionStepCount *0.2f,
            sessionStepCount *0.1f,
        )
        val entries = updateSteps.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
        val dataSet = BarDataSet(entries,"Steps"). apply {
            color = Color.CYAN
            valueTextSize = 10f
        }
        barChart.apply {
            data = BarData( dataSet)
            invalidate()
        }
    }
    private fun toggleDemoMode(){
        isDemoMode = !isDemoMode
        if (isDemoMode) {
            demoHandler.post(demoRunnable)
        }else{
            demoHandler.removeCallbacks(demoRunnable)
        }
    }

    private val demoRunnable = object : Runnable {
        private var compassAngle = 0f
        override fun run() {
            if (!isDemoMode) return
            val t = System.currentTimeMillis()
            handleAccelerometer(floatArrayOf(
                (Math.sin(t / 200.0) * 3).toFloat(),
                (Math.sin(t / 200.0) * 3).toFloat(),
                9.8f + (Math.sin(t / 150.0) * 4).toFloat(),
            ))
            compassAngle = (compassAngle + 5f) % 360f
            directionText = "Direction: ${getDirection(compassAngle)}"
            sessionStepCount += 2
            updateHealthMetrics()
            updateChartWithSteps()
            demoHandler.postDelayed(this, 500)
        }
    }


    private fun loadHistory(){
        CoroutineScope (Dispatchers.IO).launch {
            val sessions = database.sessionDao().getRecentSessions()
            withContext(Dispatchers.Main) { sessionHistory = sessions}
        }
    }

    private fun startCalibration() {
        calibrationSamples.clear() // discard any previous sample
        isCalibrating = true
    }

    private fun finishCalibration() {
        isCalibrating = false
        baselineX = calibrationSamples.map{ it[0] }.average().toFloat()
        baselineY = calibrationSamples.map{ it[1] }.average().toFloat()
        baselineZ = calibrationSamples.map{ it[2] }.average().toFloat()
        calibrationSamples.clear()
    }

    // Reads the current session, writes it to Room, refreshes the history
    private fun saveSession() {
        if (sessionStepCount == 0) {
            Toast.makeText(this, "No steps - walk around.", Toast.LENGTH_SHORT).show()
            return
        }
        val session = SessionEntity(
            timestamp = System.currentTimeMillis(),
            steps = sessionStepCount,
            calories = calculateCalories(sessionStepCount),
            distancekm = calculateDistance(sessionStepCount)
        )
        CoroutineScope(Dispatchers.IO).launch {
            database.sessionDao().insertSession(session)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Session saved!", Toast.LENGTH_SHORT).show()
                loadHistory()
            }
        }
    }


}

@Composable
fun FitnessTrackerScreen(
    motionText: String,
    gyroText: String,
    directionText: String,
    onRefreshChartClick: () -> Unit,
    onChartCreated: (BarChart) -> Unit,
    stepCountText: String,
    caloriesText: String,
    distanceText: String,
    isDemoMode: Boolean,
    onDemoModeClick: () -> Unit,
    accuracyText: String,
    isCalibrating: Boolean,
    onCalibrateClick: () -> Unit,
    sessionHistory: List<SessionEntity>,
    onSaveSessionClick: () -> Unit

) {                                         // month, day, hour:minutes, am/pm
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(text = motionText, fontSize = 20.sp, modifier = Modifier.padding(bottom = 20.dp))
        Text(text = gyroText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 20.dp))
        Text(text = directionText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 20.dp))

        // accuracy Warning - hidden until on accuracy changed fires
        if (accuracyText.isNotEmpty()) {
            Text(text = accuracyText, fontSize = 14.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }

        // calibration
        Button(
            onClick = onCalibrateClick, enabled = !isCalibrating, modifier = Modifier.padding(bottom = 20.dp)
        ){
            Text(if (isCalibrating) "Calibrating..." else "Calibrating")
        }

        // class 3
        Text(text = stepCountText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text(text = caloriesText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Text(text = distanceText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 20.dp))

        // MPAndroid Barchart - android view bridge a legacy view into compose
        AndroidView(
            factory = { ctx -> BarChart(ctx).also(onChartCreated) },
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(bottom = 16.dp)

        )
        Button(
            onClick = onRefreshChartClick,
            modifier = Modifier
                .padding(bottom = 20.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text("Refresh Chart")
        }

        //  class 3

        Button(
            onClick = onDemoModeClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDemoMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.align (Alignment.CenterHorizontally)
        ) {
            Text(if (isDemoMode) "Stop Demo" else "Demo Mode")
        }

        // Session History - saves session button + list from Room
        Button(onClick = onSaveSessionClick, modifier = Modifier.padding(bottom = 20.dp).align(Alignment.CenterHorizontally)){
            Text("Save Session")
        }
        Text(text = "Session History", fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp ))
        if (sessionHistory.isEmpty()){
            Text("No sessions saved yet. Walk around.", fontSize = 14.sp)
        }else {
            sessionHistory.forEach { s ->
                Text(
                    text = "${fmt.format(Date(s.timestamp))} | ${s.steps} Steps | ${"%.1f".format(s.calories)} kcal | ${"%.2f".format(s.distancekm)} km",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

    }
}
