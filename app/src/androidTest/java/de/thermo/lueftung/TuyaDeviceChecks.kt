package de.thermo.lueftung

import android.content.Context
import org.json.JSONObject

object TuyaDeviceChecks {
    fun run(context:Context) {
        val at=System.currentTimeMillis()
        val fixture="""{"source":"TUYA LIVE","fetchedAt":$at,"error":null,"accountDeviceCount":3,"devices":[
          {"id":"fixture-climate","name":"T&H fixture","role":"climate","room":"living","verified":true,"online":true,"points":[{"code":"va_temperature","value":237,"normalized":23.7,"type":"Integer","unit":"°C","scale":1,"read":true,"write":false,"at":$at}]},
          {"id":"fixture-window","name":"Window fixture","role":"window","room":"living","verified":true,"online":true,"points":[{"code":"doorcontact_state","value":"Open","normalized":"Open","type":"Enum","read":true,"write":false,"at":$at}]},
          {"id":"fixture-heater","name":"HY18 fixture","role":"heater","room":"living","verified":true,"online":true,"points":[{"code":"switch","value":true,"normalized":true,"type":"Boolean","at":$at},{"code":"work_state","value":"heat_off","normalized":"heat_off","type":"Enum","at":$at}]}
        ],"climate":[{"room":"living","temperature":23.7,"humidity":52,"at":$at,"sensorIds":["fixture-climate"]}]}"""
        val parsed=TuyaRepository.parse(JSONObject(fixture))
        check(!parsed.climate.getValue("living").isDemo)
        check(kotlin.math.abs(parsed.climate.getValue("living").temperatureC-23.7f)<0.001f)
        check(parsed.devices[1].window()?.first==true)
        check(parsed.devices[2].heating().devicePower==true && parsed.devices[2].heating().isActivelyHeating==false)
        check(parsed.devices.all { it.error==null })
        val broken=JSONObject(fixture)
        broken.getJSONArray("devices").getJSONObject(0).put("online",false)
        check(TuyaRepository.parse(broken).climate.isEmpty())
        val local=JSONObject(fixture).put("source","HOME ASSISTANT LOCAL")
        check(TuyaRepository.parse(local).provider=="HOME ASSISTANT LOCAL")
        val settings=TuyaConnectionSettings(context,"validation_tuya_bridge")
        val testToken="TEST_ONLY_NOT_A_REAL_TOKEN_12345678901234567890"
        try {
            settings.save("https://example.invalid",testToken)
            check(settings.token()==testToken && !settings.live)
            val saved=context.getSharedPreferences("validation_tuya_bridge",Context.MODE_PRIVATE).getString("token",null)
            check(saved!=testToken && !saved.isNullOrBlank())
            check(runCatching { settings.save("http://example.invalid",testToken) }.isFailure)
            check(runCatching { settings.save("https://example.invalid/?secret=x",testToken) }.isFailure)
            check(runCatching { settings.save("https://example.invalid","short") }.isFailure)
        } finally {
            settings.clear()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null);deleteEntry("thermo-bridge-read:validation_tuya_bridge") }
        }
        // Parsing fixtures never publishes fake live values to user history or sensors.
    }
}
