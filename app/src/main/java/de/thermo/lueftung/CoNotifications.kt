package de.thermo.lueftung

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Separate warning channel. This app and cloud polling never replace a certified detector. */
object CoPolicy {
    fun alarm(device:TuyaDevice,now:Long):Boolean? {
        if(device.role!="co" || !device.online || !device.verified || device.error!=null) return null
        val state=device.point("co_state","co_status") ?: return null
        if(!TuyaPolicy.fresh(state.at,now)) return null
        return when((state.normalized as? String)?.lowercase()) { "alarm"->true;"normal"->false;else->null }
    }
}
object CoNotifications {
    fun evaluate(context:Context,device:TuyaDevice) {
        val alarm=CoPolicy.alarm(device,System.currentTimeMillis()) ?: return
        val prefs=context.getSharedPreferences("co_alerts",Context.MODE_PRIVATE)
        if(!alarm) { prefs.edit().putBoolean(device.id,false).apply();return }
        if(prefs.getBoolean(device.id,false)) return
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel("co-warning","CO-Gerätewarnungen",NotificationManager.IMPORTANCE_HIGH))
        manager.notify(device.id.hashCode(),NotificationCompat.Builder(context,"co-warning").setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle("CO-Alarm gemeldet · ${device.name}").setContentText("Warnmelder vor Ort beachten. Bereich verlassen und Hilfe rufen. App-Anzeige ersetzt keinen CO-Warnmelder.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Das Gerät meldet CO-Alarm. Warnmelder vor Ort beachten. Bereich verlassen und Hilfe rufen. Die App ist kein zertifiziertes CO-Sicherheitssystem."))
            .setAutoCancel(false).build())
        prefs.edit().putBoolean(device.id,true).apply()
    }
}
