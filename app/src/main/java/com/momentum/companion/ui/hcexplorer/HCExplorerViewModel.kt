package com.momentum.companion.ui.hcexplorer

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.reflect.KClass

data class RecordDetail(
    val startTime: String,
    val endTime: String,
    val values: String,
    val source: String,
)

data class RecordTypeResult(
    val typeName: String,
    val count: Int,
    val records: List<RecordDetail>,
    val error: String? = null,
)

data class HCExplorerUiState(
    val date: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val results: List<RecordTypeResult> = emptyList(),
    val error: String? = null,
    val totalRecords: Int = 0,
    val typesWithData: Int = 0,
)

@HiltViewModel
class HCExplorerViewModel @Inject constructor(
    private val client: HealthConnectClient?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HCExplorerUiState())
    val uiState: StateFlow<HCExplorerUiState> = _uiState.asStateFlow()

    private val zone = ZoneId.systemDefault()

    private val recordTypes: List<Pair<String, KClass<out Record>>> = listOf(
        "ActiveCaloriesBurned" to ActiveCaloriesBurnedRecord::class,
        "BasalBodyTemperature" to BasalBodyTemperatureRecord::class,
        "BasalMetabolicRate" to BasalMetabolicRateRecord::class,
        "BloodGlucose" to BloodGlucoseRecord::class,
        "BloodPressure" to BloodPressureRecord::class,
        "BodyFat" to BodyFatRecord::class,
        "BodyTemperature" to BodyTemperatureRecord::class,
        "BodyWaterMass" to BodyWaterMassRecord::class,
        "BoneMass" to BoneMassRecord::class,
        "CervicalMucus" to CervicalMucusRecord::class,
        "CyclingPedalingCadence" to CyclingPedalingCadenceRecord::class,
        "Distance" to DistanceRecord::class,
        "ElevationGained" to ElevationGainedRecord::class,
        "ExerciseSession" to ExerciseSessionRecord::class,
        "FloorsClimbed" to FloorsClimbedRecord::class,
        "HeartRate" to HeartRateRecord::class,
        "HeartRateVariabilityRmssd" to HeartRateVariabilityRmssdRecord::class,
        "Height" to HeightRecord::class,
        "Hydration" to HydrationRecord::class,
        "IntermenstrualBleeding" to IntermenstrualBleedingRecord::class,
        "LeanBodyMass" to LeanBodyMassRecord::class,
        "MenstruationFlow" to MenstruationFlowRecord::class,
        "MenstruationPeriod" to MenstruationPeriodRecord::class,
        "Nutrition" to NutritionRecord::class,
        "OvulationTest" to OvulationTestRecord::class,
        "OxygenSaturation" to OxygenSaturationRecord::class,
        "PlannedExerciseSession" to PlannedExerciseSessionRecord::class,
        "Power" to PowerRecord::class,
        "RespiratoryRate" to RespiratoryRateRecord::class,
        "RestingHeartRate" to RestingHeartRateRecord::class,
        "SexualActivity" to SexualActivityRecord::class,
        "SkinTemperature" to SkinTemperatureRecord::class,
        "SleepSession" to SleepSessionRecord::class,
        "Speed" to SpeedRecord::class,
        "Steps" to StepsRecord::class,
        "StepsCadence" to StepsCadenceRecord::class,
        "TotalCaloriesBurned" to TotalCaloriesBurnedRecord::class,
        "Vo2Max" to Vo2MaxRecord::class,
        "Weight" to WeightRecord::class,
        "WheelchairPushes" to WheelchairPushesRecord::class,
    )

    init {
        loadData()
    }

    fun setDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(date = date)
        loadData()
    }

    fun loadData() {
        val hcClient = client ?: run {
            _uiState.value = _uiState.value.copy(
                error = "Health Connect non disponible",
                isLoading = false,
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val date = _uiState.value.date
            val startInstant = date.atStartOfDay(zone).toInstant()
            val endInstant = date.plusDays(1).atStartOfDay(zone).toInstant()
            val timeRange = TimeRangeFilter.between(startInstant, endInstant)

            val results = recordTypes.map { (name, klass) ->
                readRecordType(hcClient, name, klass, timeRange)
            }

            val totalRecords = results.sumOf { it.count }
            val typesWithData = results.count { it.count > 0 }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                results = results,
                totalRecords = totalRecords,
                typesWithData = typesWithData,
            )
        }
    }

    private suspend fun <T : Record> readRecordType(
        hcClient: HealthConnectClient,
        name: String,
        klass: KClass<T>,
        timeRange: TimeRangeFilter,
    ): RecordTypeResult {
        return try {
            val response = hcClient.readRecords(
                ReadRecordsRequest(
                    recordType = klass,
                    timeRangeFilter = timeRange,
                ),
            )
            RecordTypeResult(
                typeName = name,
                count = response.records.size,
                records = response.records.map { formatRecord(it) },
            )
        } catch (e: SecurityException) {
            RecordTypeResult(
                typeName = name,
                count = 0,
                records = emptyList(),
                error = "Permission non accordee",
            )
        } catch (e: Exception) {
            RecordTypeResult(
                typeName = name,
                count = 0,
                records = emptyList(),
                error = e.message ?: "Erreur inconnue",
            )
        }
    }

    fun exportJson(): String {
        val state = _uiState.value
        val json = JSONObject()
        json.put("date", state.date.toString())
        json.put("totalRecords", state.totalRecords)
        json.put("typesWithData", state.typesWithData)
        val resultsArray = JSONArray()
        for (result in state.results) {
            val resultObj = JSONObject()
            resultObj.put("typeName", result.typeName)
            resultObj.put("count", result.count)
            resultObj.put("error", result.error ?: JSONObject.NULL)
            val recordsArray = JSONArray()
            for (record in result.records) {
                val recordObj = JSONObject()
                recordObj.put("source", record.source)
                recordObj.put("values", record.values)
                recordsArray.put(recordObj)
            }
            resultObj.put("records", recordsArray)
            resultsArray.put(resultObj)
        }
        json.put("results", resultsArray)
        return json.toString(2)
    }

    private fun formatRecord(record: Record): RecordDetail {
        val source = record.metadata.dataOrigin.packageName
        return RecordDetail(
            startTime = "",
            endTime = "",
            values = record.toString(),
            source = source,
        )
    }
}
