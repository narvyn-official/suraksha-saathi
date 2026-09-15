package com.narvyn.suraksha
import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
object CredentialVerifier {
 fun verify(context:Context,raw:String,now:Long=System.currentTimeMillis()):JSONObject {
  require(raw.length<=6000){"Credential too large"}
  val token=raw.trim().removePrefix("SURAKSHA:CREDENTIAL:");val p=token.split('.');require(p.size==3){"Invalid credential"}
  val trust=JSONObject(context.assets.open("trusted-issuer.json").bufferedReader().use{it.readText()})
  fun decode(s:String)=Base64.decode(s,Base64.URL_SAFE or Base64.NO_WRAP)
  val header=JSONObject(String(decode(p[0])));require(header.getString("alg")=="ES256"&&header.getString("kid")==trust.getString("kid")){"Unknown issuer"}
  val key=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(trust.getString("spki"),Base64.DEFAULT)))
  val verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);verifier.update("${p[0]}.${p[1]}".toByteArray(Charsets.US_ASCII));require(verifier.verify(toDer(decode(p[2])))){"Invalid signature"}
  val payload=JSONObject(String(decode(p[1])));require(payload.getString("iss")==trust.getString("issuer")&&payload.getString("kind")=="pilot-simulation"&&payload.getString("practical")=="not-assessed"){"Invalid scope"}
  require(payload.getString("moduleId") in listOf("fire","gas","machinery","ppe","emergency"));payload.getString("id")
  val validity=validity(payload,now)
  return payload.put("token",token).put("signatureValid",true).put("expiryStatus",validity.getString("expiryStatus"))
   .put("validityCheckedAt",now).put("issuedInFuture",validity.getBoolean("issuedInFuture"))
 }
 /** Validity is separate from signature verification. These custom pilot timestamps are Unix milliseconds. */
 fun validity(payload:JSONObject,now:Long=System.currentTimeMillis()):JSONObject {
  require(now in 0..MAX_DATE){"Invalid verification time"}
  fun timestamp(key:String):Long {
   val value=payload.opt(key)
   require(value is Number){"Invalid credential $key"}
   val number=value.toDouble()
   require(number.isFinite() && number>=0 && number<=MAX_DATE.toDouble() && number%1.0==0.0){"Invalid credential $key"}
   return number.toLong()
  }
  val issued=timestamp("iat")
  val expiry=if(payload.has("expiresAt"))timestamp("expiresAt").also { require(it>issued){"Expiry must follow issuance"} }else null
  val status=when { expiry==null->"not-recorded";now>=expiry->"expired";else->"within-validity" }
  return JSONObject().put("expiryStatus",status).put("validityCheckedAt",now).put("issuedInFuture",issued>now)
 }
 private const val MAX_DATE=8640000000000000L
 fun toDer(raw:ByteArray):ByteArray {
  require(raw.size==64){"Invalid signature size"}
  fun integer(bytes:ByteArray):ByteArray {var i=0;while(i<bytes.size-1&&bytes[i]==0.toByte())i++;val stripped=bytes.copyOfRange(i,bytes.size);return if(stripped[0].toInt() and 128!=0)byteArrayOf(0)+stripped else stripped}
  val r=integer(raw.copyOfRange(0,32));val s=integer(raw.copyOfRange(32,64));return byteArrayOf(0x30,(r.size+s.size+4).toByte(),2,r.size.toByte())+r+byteArrayOf(2,s.size.toByte())+s
 }
}
