package de.thermo.lueftung

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App holds only its revocable read-only bridge token. Tuya Access Secret never enters this APK. */
class TuyaConnectionSettings(context:Context,private val storageName:String="tuya_bridge") {
    private val prefs=context.getSharedPreferences(storageName,Context.MODE_PRIVATE)
    val url get()=prefs.getString("url","") ?: ""
    val live get()=prefs.getBoolean("live",false)
    fun setLive(enabled:Boolean) { prefs.edit().putBoolean("live",enabled).apply() }
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("thermo-bridge-read:$storageName",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("thermo-bridge-read:$storageName",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(endpoint:String,token:String) {
        val uri=java.net.URI(endpoint.trim())
        require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.query==null && uri.fragment==null && (uri.path.isNullOrEmpty() || uri.path=="/")) { "HTTPS-Adresse ohne Pfad, Passwort oder Parameter verwenden" }
        require(token.length>=32 && token.none { it.isWhitespace() }) { "Lesetoken: mindestens 32 Zeichen ohne Leerzeichen" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val data=cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("url",endpoint.trim().trimEnd('/')).putString("token",Base64.encodeToString(data,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putBoolean("live",false).apply()
    }
    fun token():String {
        val data=prefs.getString("token",null) ?: return ""
        val iv=prefs.getString("iv",null) ?: return ""
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));String(doFinal(Base64.decode(data,Base64.NO_WRAP)),Charsets.UTF_8) }
        } catch(_:Exception) { "" } // backup/key loss requires reconfiguration; never print secret
    }
    fun clear() { prefs.edit().clear().apply() }
}
