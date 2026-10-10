package de.smarty.camviewer

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * ONVIF-PTZ-Client fuer Kameras mit Schwenk/Neigen/Zoom.
 * Portiert aus dem verifizierten ptz.py (WS-Security Digest-Auth + ContinuousMove/GotoPreset/Stop).
 */
object OnvifPtz {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class Preset(val token: String, val name: String)

    /** XML-Sonderzeichen escapen — Passwoerter/Namen mit & < > " ' wuerden
     *  sonst die SOAP-Envelope zerbrechen oder XML-Injection ermoeglichen. */
    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    // ---- WS-Security (PasswordDigest) ----
    private fun wsse(user: String, pw: String): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        val digestInput = nonce + created.toByteArray(Charsets.UTF_8) + pw.toByteArray(Charsets.UTF_8)
        val digest = Base64.encodeToString(
            MessageDigest.getInstance("SHA-1").digest(digestInput), Base64.NO_WRAP)
        val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        return "<wsse:Security xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" " +
            "soap-env:mustUnderstand=\"1\"><wsse:UsernameToken><wsse:Username>${xmlEscape(user)}</wsse:Username>" +
            "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">$digest</wsse:Password>" +
            "<wsse:Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">$nonceB64</wsse:Nonce>" +
            "<wsu:Created xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">$created</wsu:Created>" +
            "</wsse:UsernameToken></wsse:Security>"
    }

    private fun envelope(user: String, pw: String, body: String): String {
        return "<?xml version=\"1.0\"?>" +
            "<soap-env:Envelope xmlns:soap-env=\"http://www.w3.org/2003/05/soap-envelope\" " +
            "xmlns:tt=\"http://www.onvif.org/ver10/schema\" " +
            "xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" " +
            "xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\">" +
            "<soap-env:Header>${wsse(user, pw)}</soap-env:Header>" +
            "<soap-env:Body>$body</soap-env:Body></soap-env:Envelope>"
    }

    private suspend fun soap(cam: Camera, action: String, body: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val host = cam.ptzHost
                val port = cam.ptzPort
                val path = cam.ptzPath
                val url = "http://$host:$port$path"
                val env = envelope(cam.ptzUser, cam.ptzPass, body)
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/soap+xml; charset=utf-8")
                    .addHeader("SOAPAction", "\"$action\"")
                    .post(env.toRequestBody("application/soap+xml; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val txt = resp.body?.string() ?: ""
                    if (resp.isSuccessful) Result.success(txt) else Result.failure(Exception("HTTP ${resp.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun gotoPreset(cam: Camera, token: String): Result<String> {
        val body = "<tptz:GotoPreset><tptz:ProfileToken>${xmlEscape(cam.ptzProfile)}</tptz:ProfileToken>" +
            "<tptz:PresetToken>${xmlEscape(token)}</tptz:PresetToken><tptz:Speed>1.0</tptz:Speed></tptz:GotoPreset>"
        return soap(cam, "http://www.onvif.org/ver20/ptz/wsdl/GotoPreset", body)
    }

    suspend fun continuousMove(cam: Camera, pan: Float, tilt: Float, zoom: Float): Result<String> {
        val body = "<tptz:ContinuousMove><tptz:ProfileToken>${cam.ptzProfile}</tptz:ProfileToken>" +
            "<tptz:Velocity>" +
            "<tt:PanTilt x=\"$pan\" y=\"$tilt\" xmlns:tt=\"http://www.onvif.org/ver10/schema\"/>" +
            "<tt:Zoom x=\"$zoom\" xmlns:tt=\"http://www.onvif.org/ver10/schema\"/>" +
            "</tptz:Velocity><tptz:Timeout>PT5S</tptz:Timeout></tptz:ContinuousMove>"
        return soap(cam, "http://www.onvif.org/ver20/ptz/wsdl/ContinuousMove", body)
    }

    suspend fun stop(cam: Camera): Result<String> {
        val body = "<tptz:Stop><tptz:ProfileToken>${xmlEscape(cam.ptzProfile)}</tptz:ProfileToken>" +
            "<tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom></tptz:Stop>"
        return soap(cam, "http://www.onvif.org/ver20/ptz/wsdl/Stop", body)
    }

    suspend fun getPresets(cam: Camera): List<Preset> = withContext(Dispatchers.IO) {
        val body = "<tptz:GetPresets><tptz:ProfileToken>${xmlEscape(cam.ptzProfile)}</tptz:ProfileToken></tptz:GetPresets>"
        val res = soap(cam, "http://www.onvif.org/ver20/ptz/wsdl/GetPresets", body)
        res.getOrNull()?.let { parsePresets(it) } ?: emptyList()
    }

    private fun parsePresets(xml: String): List<Preset> {
        val out = mutableListOf<Preset>()
        // Tolerant: Preset-Elemente mit token-Attribut oder <token>-Kind + <Name>-Kind
        val presetRegex = Regex("""<[^>]*:Preset[^>]*>.*?</[^>]*:Preset>""", RegexOption.DOT_MATCHES_ALL)
        val tokenAttrRegex = Regex("""token="([^"]*)"""")
        val tokenChildRegex = Regex("""<[^>]*:token>([^<]*)</[^>]*:token>""")
        val nameRegex = Regex("""<[^>]*:Name>([^<]*)</[^>]*:Name>""")
        for (m in presetRegex.findAll(xml)) {
            val block = m.value
            val token = tokenAttrRegex.find(block)?.groupValues?.get(1)
                ?: tokenChildRegex.find(block)?.groupValues?.get(1)
                ?: continue
            val name = nameRegex.find(block)?.groupValues?.get(1) ?: token
            out.add(Preset(token, name))
        }
        return out
    }
}
