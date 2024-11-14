package com.example.weatherapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.weatherapp.ui.theme.WeatherAppTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnSuccessListener
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Модель данных для текущей погоды
data class WeatherResponse(
    val current: Current,
    val location: Location
)

data class Current(
    val temp_c: Double,
    val temp_f: Double,
    val humidity: Int,
    val feelslike_c: Double
)

data class Location(
    val name: String
)

// Интерфейс API для получения данных о погоде
interface WeatherApi {
    @GET("current.json")
    suspend fun getCurrentWeather(
        @Query("key") apiKey: String,
        @Query("q") city: String,
        @Query("aqi") aqi: String
    ): WeatherResponse
}

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Для работы с разрешениями
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                getLocation()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private var currentUnit by mutableStateOf("metric") // "metric" для Цельсия, "imperial" для Фаренгейта
    private var cityName by mutableStateOf("")
    private var temperatureCelsius by mutableStateOf("Loading...")
    private var temperatureFahrenheit by mutableStateOf("Loading...")
    private var locationName by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация fusedLocationClient для получения геолокации
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Запрос разрешения на геолокацию
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLocation()
        } else {
            // Запрашиваем разрешение, если оно еще не было предоставлено
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            WeatherAppTheme {
                WeatherScreen()
            }
        }
    }

    // Получение текущего местоположения
    private fun getLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener(this, OnSuccessListener { location ->
                location?.let {
                    val latitude = it.latitude
                    val longitude = it.longitude
                    // Можно использовать геолокацию для поиска города или просто запросить погоду по городу
                    fetchWeatherData("London")
                } ?: run {
                    Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: SecurityException) {
            // Обработка ситуации, когда разрешение на доступ к местоположению не предоставлено
            Toast.makeText(this, "Permission denied for location", Toast.LENGTH_SHORT).show()
        }
    }

    // Функция для получения данных о погоде через Retrofit
    private fun fetchWeatherData(city: String) {
        // Создаем экземпляр Retrofit с правильным базовым URL
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val weatherApi = retrofit.create(WeatherApi::class.java)
        val apiKey = "b2a18e6cd27044d788b171930241411"

        // Запрос данных о погоде в отдельном потоке
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = weatherApi.getCurrentWeather(apiKey, city, "no")
                if (response != null) {
                    withContext(Dispatchers.Main) {
                        updateUI(response)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No data received", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Обновление UI с полученными данными
    private fun updateUI(weatherResponse: WeatherResponse) {
        temperatureCelsius = "${weatherResponse.current.temp_c} °C"
        temperatureFahrenheit = "${weatherResponse.current.temp_f} °F"
        locationName = weatherResponse.location.name
    }

    // Экран с отображением погоды
    @Composable
    fun WeatherScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Поиск города
            Text("Enter city name:")
            var text by remember { mutableStateOf("") }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
            Button(onClick = {
                cityName = text
                fetchWeatherData(cityName)  // Получаем погоду для указанного города
            }) {
                Text("Search")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Weather in $locationName", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "$temperatureCelsius", style = MaterialTheme.typography.headlineMedium)
            Text(text = "$temperatureFahrenheit", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка для переключения между Цельсием и Фаренгейтом
            IconButton(onClick = {
                currentUnit = if (currentUnit == "metric") "imperial" else "metric"
                fetchWeatherData(cityName)
            }) {
                Text(text = "Toggle °C / °F")
            }
        }
    }
}
