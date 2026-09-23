package com.narvyn.suraksha

import android.content.Context
import android.content.pm.ApplicationInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.HttpCookie
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Same-origin API session. Passwords are never persisted; cookies are encrypted by Android Keystore. */
class AdminClient(private val context:Context) {
    private val prefs=context.getSharedPreferences("admin_session",Context.MODE_PRIVATE)
    var base:String
        get()=prefs.getString("server",null) ?: if(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0) "http://localhost:5173" else ""
        private set(value){prefs.edit().putString("server",value).apply()}
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("suraksha_admin_session",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("suraksha_admin_session",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun configure(value:String) {
        val uri=URI(value.trim());val debug=context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0
        require(uri.host!=null && uri.userInfo==null && uri.query==null && uri.fragment==null && uri.path.orEmpty() in listOf("","/") && (uri.scheme=="https" || debug&&uri.scheme=="http"&&uri.host in listOf("localhost","127.0.0.1"))) { "Enter your training centre’s HTTPS server address." }
        val clean=uri.toString().trimEnd('/');if(clean!=base){clear();base=clean}
    }
    companion object { @Volatile private var sessionGeneration=0L; private var sessionCookies:JSONObject?=null; private var sessionOrigin:String?=null }
    fun remember(value:Boolean){prefs.edit().putBoolean("remember",value).apply();if(!value)prefs.edit().remove("cookies").apply()}
    private fun cookies():JSONObject { return try {
        if(!prefs.getBoolean("remember",true))return if(sessionOrigin==base)JSONObject(sessionCookies?.toString()?:"{}")else JSONObject()
        val saved=prefs.getString("cookies",null)
        if(saved==null)JSONObject() else {
            val parts=saved.split('.');val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
            JSONObject(String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8))
        }
    } catch(_:Exception){prefs.edit().remove("cookies").apply();JSONObject()}
    }
    private fun saveCookies(value:JSONObject) {
        if(!prefs.getBoolean("remember",true)){sessionCookies=JSONObject(value.toString());sessionOrigin=base;return}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("cookies",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply()
    }
    fun hasSession():Boolean { val jar=cookies();val now=System.currentTimeMillis();return jar.keys().asSequence().any{it.endsWith("session_token")&&jar.getJSONObject(it).optLong("expires",0)>now} }
    fun clear(){sessionGeneration++;prefs.edit().remove("cookies").apply();sessionCookies=null;sessionOrigin=null}
    fun call(path:String,body:JSONObject?=null,method:String=if(body==null)"GET" else "POST"):JSONObject {
        require(base.isNotBlank()){ "Connect your training centre server first." }
        require(path.startsWith("/api/")&&!path.contains(".."))
        val origin=base;val generation=sessionGeneration
        val connection=URI(origin+path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method;connection.instanceFollowRedirects=false;connection.connectTimeout=12000;connection.readTimeout=20000
            connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Origin",base)
            val jar=cookies();val now=System.currentTimeMillis()
            val names=jar.keys().asSequence().toList();for(name in names)if(jar.getJSONObject(name).optLong("expires",Long.MAX_VALUE)<now)jar.remove(name)
            if(jar.length()>0)connection.setRequestProperty("Cookie",jar.keys().asSequence().joinToString("; "){"$it=${jar.getJSONObject(it).getString("value")}"})
            if(body!=null){connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val status=connection.responseCode
            for((name,values) in connection.headerFields)if(name.equals("Set-Cookie",true))for(raw in values)for(cookie in HttpCookie.parse(raw)) {
                if(cookie.hasExpired())jar.remove(cookie.name) else jar.put(cookie.name,JSONObject().put("value",cookie.value).put("expires",if(cookie.maxAge<0)Long.MAX_VALUE else now+cookie.maxAge.coerceAtMost(31536000)*1000))
            }
            if(generation==sessionGeneration&&origin==base)saveCookies(jar)
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            val bytes=stream?.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(out.size()<=4_000_000){val count=input.read(buffer);if(count<0)break;out.write(buffer,0,count)};out.toByteArray()}?:byteArrayOf();require(bytes.size<=4_000_000){"Response is too large. Refine the records shown."}
            val data=try{JSONObject(String(bytes,Charsets.UTF_8))}catch(_:Exception){
                if(status in 200..299)throw IllegalStateException("The service returned an unreadable response. Check your server address and try again.")
                JSONObject()
            }
            if(status==401){clear();if(path.startsWith("/api/auth/sign-in"))throw IllegalStateException(data.optString("message","Check your email and password."));throw SignedOut()}
            if(status==429)throw IllegalStateException("Too many attempts. Wait a minute before trying again.")
            if(status>=500)throw IllegalStateException("The account service is unavailable. Try again later or contact your training centre.")
            if(status !in 200..299)throw IllegalStateException(data.optString("message",data.optString("error","Could not complete the request ($status).")))
            return data
        } finally {connection.disconnect()}
    }
    class SignedOut:Exception("Your session has ended. Sign in again.")
}
