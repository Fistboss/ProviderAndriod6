package com.s3.providerandriod6

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.*
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.*
import com.s3.providerandriod6.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Timer

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val REQUEST_LOCATION_PERMISSION = 1001
    private val REQUEST_IGNORE_BATTERY_OPTIMIZATION = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkBatteryOptimization()
        val setmgr = SetManager(this@MainActivity)
        val logsClass = LogWriter(this@MainActivity)
        if(checkSelfPermission(this, SEND_SMS) != PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(SEND_SMS),101)
        }
        logsClass.writeToLog("welcome!")
        binding.button.setOnClickListener {
            try {
                startLocationTracker()
                logsClass.writeToLog("starting tracker...")
            } catch (e: Exception) {
                binding.textView.text = "Ошибка: ${e.localizedMessage}"
            }
        }
        binding.buttonChangeNumber.setOnClickListener {
            showInputNumberDialog(this, "Введите номер", "+7...") { text ->
                // text - то что ввел пользователь
                setmgr.put("target_number", text)
                //editNumber.setText(text)
                Toast.makeText(this, "Номер сохранен: $text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setTelegram(view: View){
        val setmgr = SetManager(this@MainActivity)
        if (view.isActivated) setmgr.putBoolean("telegram",true)
        if (!view.isActivated) setmgr.putBoolean("telegram",false)
    }

    private fun checkBatteryOptimization() {
        val logsClass = LogWriter(this@MainActivity)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                logsClass.writeToLog("Cannot open battery optimization settings")
            }
        }
    }

    private fun startLocationTracker() {
        val logsClass = LogWriter(this@MainActivity)
        if (checkLocationPermission()) {
            if (checkPermissions()) {
                startLocationService()
                logsClass.writeToLog("starting service...")
            }else{
                requestCallsPermissions()
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun checkLocationPermission(): Boolean { return checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= 28) {
            checkSelfPermission(this, ANSWER_PHONE_CALLS) == 0 &&
                    checkSelfPermission(this, READ_PHONE_STATE) == 0
        } else {
            checkSelfPermission(this, CALL_PHONE) == 0
        }
    }

    private fun requestCallsPermissions() {
        if (Build.VERSION.SDK_INT >= 28) {
            ActivityCompat.requestPermissions(this,
                arrayOf(ANSWER_PHONE_CALLS, READ_PHONE_STATE),
                1002)
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(CALL_PHONE),
                1002)
        }
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    private fun startLocationService() {
        val logsClass = LogWriter(this@MainActivity)
        Intent(this, LocationTrackerService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
                logsClass.writeToLog("starting foreground service after Q...")
            } else {
                startService(intent)
                logsClass.writeToLog("starting foreground service")
            }
            updateUI(true)
        }
    }

    private fun updateUI(isRunning: Boolean) {
        binding.statusText.text = if (isRunning) "Статус: активен" else "Статус: неактивен"
        binding.button.setBackgroundColor(getColor(this, if (isRunning) R.color.green else R.color.red))
    }
    fun showInputNumberDialog(context: Context, title: String, hint: String = "", onResult: (String) -> Unit) {
        val input = EditText(context).apply {
            this.hint = hint
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                onResult(input.text.toString())
            }
            .setNegativeButton("Отмена", null)
            .show()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                    startLocationService()
                } else {
                    Toast.makeText(
                        this,
                        "Для работы трекера необходимо разрешение",
                        Toast.LENGTH_LONG
                    ).show()
                    updateUI(false)
                } } }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATION) {
            Toast.makeText(
                this,
                "ОПТИМИЗАЦИЯ БАТАРЕИ ИГНОРИРОВАНА",
                Toast.LENGTH_SHORT
            ).show()
        } }
}

class LocationTrackerService : Service() {
    private lateinit var locationManager: LocationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private var lastUpdateId = 0
    private var updateTimer: Timer? = null
    private var currentLocation: Location? = null
    private var unavailabledNetworkCounter = 0
    private lateinit var logsClass: LogWriter
    private lateinit var setmgr: SetManager
    private var updateJob: Job? = null
    private var counterSend = 0
    private val supportedCommands = "Supported commands: /getlocation, /battery, /1hour, /5hour, /12hour"
    private var everyhoursheartbeat = 120
    private var descheartbeat = "It's every hour heartbeat."

    override fun onCreate() {
        super.onCreate()
        try {
            logsClass = LogWriter(this@LocationTrackerService)
            setmgr = SetManager(this@LocationTrackerService)
            startForegroundService()
            acquireOptimizedWakeLock()
            if (checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED &&
                checkSelfPermission(ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED
            ) {
                try {
                    startLocationUpdates()
                } catch (er2: Exception) {
                    logsClass.writeToLog(er2.localizedMessage.toString())
                }
                logsClass.writeToLog("telegram key load...")
                logsClass.writeToLog(
                    "telegram key is " + setmgr.getBoolean("telegram", false).toString()
                )
                if (setmgr.getBoolean("telegram", false)) {
                    startUpdateTimer()
                    logsClass.writeToLog("Service with telegram started")
                } else {
                    logsClass.writeToLog("Service started without telegram")
                }

            } else {
                logsClass.writeToLog("Service stopped, onCreate permissions denied")
                stopSelf()
            }
        }catch (er3: Exception) {logsClass.writeToLog(er3.localizedMessage.toString())}
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
        return START_STICKY // Система перезапустит сервис если он был убит
    }


    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val number = "Coming soon"
                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                    endCall(context)
                    onCallIntercepted(number)
                }
            }
        }



        private fun endCall(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val telecomManager = context.getSystemService(TELECOM_SERVICE) as TelecomManager
                    telecomManager.endCall()
                    logsClass.writeToLog("Звонок сброшен через TelecomManager")
                } else {
                    endCallLegacy(context)
                }
            } catch (e: SecurityException) {
                logsClass.writeToLog("Ошибка безопасности при сбросе: ${e.message}")
            } catch (e: Exception) {
                logsClass.writeToLog("Ошибка сброса: ${e.message}")
                try {
                    endCallLegacy(context)
                } catch (ex: Exception) {
                    logsClass.writeToLog("Не удалось сбросить звонок альтернативным методом: ${ex.message}")
                }
            }
        }

        private fun endCallLegacy(context: Context) {
            val tm = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            try {
                val c = Class.forName(tm.javaClass.name)
                val m = c.getDeclaredMethod("getITelephony")
                m.isAccessible = true
                val telephonyService = m.invoke(tm)
                val tmEndCall = telephonyService.javaClass.getDeclaredMethod("endCall")
                tmEndCall.invoke(telephonyService)
                logsClass.writeToLog("Звонок сброшен через рефлексию")
            } catch (e: Exception) {
                logsClass.writeToLog("Ошибка при использовании рефлексии: ${e.message}")
                throw e
            }
        }

        private fun onCallIntercepted(number: String?) {
            logsClass.writeToLog("Звонок перехвачен! Номер: $number")
            currentLocation?.let { sendSMSLocation(it) }
        }
    }


   private fun sendSMS(soob: String) {
        //val phoneNumber = "+79001437410"
       val phoneNumber = setmgr.get("target_number")
       logsClass.writeToLog("Target number is $phoneNumber")
       //Toast.makeText(applicationContext, "Target number is $phoneNumber", Toast.LENGTH_LONG).show()
       if (phoneNumber.isEmpty() || soob.isEmpty()) {
           // Toast.makeText(applicationContext, "Target number is lost!", Toast.LENGTH_LONG).show()
           logsClass.writeToLog("Target number is lost or message is empty!")
            return
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= 24) {
                this.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                SmsManager.getDefault()
            }

            // Отправляем SMS
            smsManager.sendTextMessage(phoneNumber, null, soob, null, null)
            logsClass.writeToLog("Message Sent")

        } catch (e: Exception) {
            logsClass.writeToLog("Error sending message " +e.localizedMessage)
        }
    }



    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        val channelId = "location_tracker_channel_${System.currentTimeMillis()}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracking your location"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(VERSION_LOCATOR)
            .setContentText("Идёт отслеживание координат...")
            .setSmallIcon(R.drawable._57)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }
            logsClass.writeToLog("Foreground service started successfully")
        } catch (e: Exception) {
            logsClass.writeToLog("Foreground service failed: ${e.message}")
        }
    }

    private fun acquireOptimizedWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocationTracker::WakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
        logsClass.writeToLog("Optimized WakeLock acquired")
    }

    private fun isDeviceInDoze(context: Context): Boolean {
        return (context.getSystemService(POWER_SERVICE) as PowerManager).isDeviceIdleMode
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED &&
            checkSelfPermission(ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED) {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            try {
                // Сетевой провайдер потому что телефон слабый,на мощном это можно удалить
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000,//15k
                    5f,//50f
                    locationListener
                )

                // GPS как уточнение на старых, на новом использвать только его
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    15000,//30k
                    5f,//50f
                    locationListener
                )
            } catch (e: Exception) {
                logsClass.writeToLog("Location error")
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.accuracy < 100) { //так как телефон слабый
                currentLocation = location
            }
        }

        override fun onProviderDisabled(provider: String) {
            logsClass.writeToLog("GPS disconnected")
        }
    }

    private fun startUpdateTimer() {
        stopUpdateTimer()
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    delay(UPDATING_TIMER)
                    counterSend += 1
                    checkForMessages() //ВРЕМЕННО ОТКЛЮЧЕНО, ЕГО ЗАМЕНЯЕТ СЛУШАТЕЛЬ ВЫЗОВОВ
                } catch (e: Exception) {
                    logsClass.writeToLog("Timer error: ${e.message}")
                }
            }
        }
    }

    private fun stopUpdateTimer() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun checkForMessages() {
        var serviceVariable1 = ""
        if (isDeviceInDoze(this@LocationTrackerService)) {
            logsClass.writeToLog("Device in deep Doze, skipping network call")
        }
        if (!isNetworkAvailable(this@LocationTrackerService)) {

            unavailabledNetworkCounter ++
            if (unavailabledNetworkCounter >= 40) {
                unavailabledNetworkCounter = 0
                logsClass.writeToLog("Network unavailable 20 minutes.")
            }

            return
        }
        val connectionTimeout = 6000
        var connection: HttpURLConnection? = null
        serviceVariable1 = URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getUpdates?offset=${lastUpdateId + 1}").toString()
        try {
            val url = URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getUpdates?offset=${lastUpdateId + 1}")
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = connectionTimeout
                requestMethod = "GET"
                useCaches = false
            }

            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                processTelegramResponse(response)
                if (counterSend >= everyhoursheartbeat){
                    counterSend = 0
                    sendHeartbeatAsync()
                }
            } else {
                logsClass.writeToLog("HTTP error: ${connection.responseCode}")
            }

        } catch (e: SocketTimeoutException) {
            logsClass.writeToLog("Timeout error: ${e.localizedMessage}")
        } catch (e: IOException) {
            logsClass.writeToLog("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            logsClass.writeToLog("Unexpected error: ${e.localizedMessage} , $serviceVariable1")
        } finally {
            connection?.disconnect()
        }
    }

    private fun sendHeartbeatAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(5000) {
                    URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=$descheartbeat For every 1, 5 or 12 hours heartbeat send message 1hour, 5hours or 12hours").openStream().close()
                }
            } catch (e: Exception) {
                logsClass.writeToLog("Heartbeat failed: ${e.message}")
            }
        }
    }

    private fun processTelegramResponse(response: String) {
        try {
            val jsonResponse = JSONObject(response)
            val updates = jsonResponse.optJSONArray("result") ?: run {
                logsClass.writeToLog("No updates in response (normal)")
                return
            }
            val lengthUpdates = updates.length()
            for (i in 0 until lengthUpdates) {
                val update = updates.getJSONObject(i)
                lastUpdateId = update.getInt("update_id")

                val message = update.optJSONObject("message")
                when (message?.optString("text")){
                    "/getlocation" ->{
                        currentLocation?.let { sendLocation(it) }
                        logsClass.writeToLog("Location sent")
                    }
                    "/battery" -> {
                        val encodedMessageB = URLEncoder.encode("Заряд: ${getBatteryPercentage()}%", "UTF-8")
                        URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=$encodedMessageB").openStream().close()
                    }
                    "/1hour" -> {
                        everyhoursheartbeat = 120
                        descheartbeat = "It's every hour heartbeat."
                        URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=The heartbeat is set every hour").openStream().close()
                    }
                    "/5hour" -> {
                        everyhoursheartbeat = 600
                        descheartbeat = "It's every 5 hours heartbeat."
                        URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=The heartbeat is set every 5 hours").openStream().close()
                    }
                    "/12hour" -> {
                        everyhoursheartbeat = 1440
                        descheartbeat = "It's every 12 hours heartbeat."
                        URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=The heartbeat is set every 12 hours").openStream().close()
                    }


                    else -> {
                        URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=$supportedCommands").openStream().close()
                    } } }
        } catch (e: JSONException) {
            logsClass.writeToLog("JSON parse error: ${e.message}")
        } catch (e: Exception) {
            logsClass.writeToLog("Response processing error: ${e.message}")
        }

    }

    private fun sendLocation(location: Location) {
        try {
            URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendLocation?chat_id=$TELEGRAM_CHAT_ID&latitude=${location.latitude}&longitude=${location.longitude}").openStream().close()
            val batteryLevel = getBatteryPercentage()
            val message = "Заряд: ${batteryLevel}%"

            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            URL("https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage?chat_id=$TELEGRAM_CHAT_ID&text=$encodedMessage").openStream().close()

        } catch (e: Exception) {
            logsClass.writeToLog("Current location can't sended. ${e.localizedMessage}")
        }
    }
    private fun sendSMSLocation(location: Location) {
        val batteryLevel = getBatteryPercentage()
        val message = "Заряд: ${batteryLevel}%"
        val locationsMessage = "${location.latitude},${location.longitude}"
        sendSMS (locationsMessage)

    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryStatus?.let {
            (it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 /
                    it.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
        } ?: -1
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            logsClass.writeToLog("ConnectivityManager not available")
            return false
        }
        val network = connectivityManager.activeNetwork
        return network?.let {
            try {
                val capabilities = connectivityManager.getNetworkCapabilities(it)
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } catch (e: SecurityException) {
                logsClass.writeToLog("SecurityException: ${e.message}")
                false
            }
        } ?: false
    }

    override fun onDestroy() {
        logsClass.writeToLog("OnDestroy called")
        unregisterReceiver(callReceiver)
        logsClass.writeToLog("callReceiver destroyed")

        stopUpdateTimer()
        logsClass.writeToLog("timer destroyed")

        if (::locationManager.isInitialized) {
            try {
                locationManager.removeUpdates(locationListener)
                logsClass.writeToLog("locationManager destroyed")

            } catch (e: Exception) {
                logsClass.writeToLog("Error removing location updates: ${e.message}")
            }
        }
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            try {
                wakeLock.release()
                logsClass.writeToLog("WakeLock released")
            } catch (e: Exception) {
                logsClass.writeToLog("Error releasing WakeLock: ${e.message}")
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val TELEGRAM_BOT_TOKEN = "80:0"
        private const val TELEGRAM_CHAT_ID = "0"
        private const val UPDATING_TIMER: Long = 30000
        private const val VERSION_LOCATOR = "GPS 1.6.1"
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (checkSelfPermission(context, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
                val serviceIntent = Intent(context, LocationTrackerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}


