package com.dokubots.drmplayer

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class StreamConfig(
    val url: String,
    val drmType: String,
    val drmLicense: String,
    val headers: Map<String, String>
)

object StreamParser {
    fun parse(input: String): StreamConfig {
        var cleanUrl = input
        var drmType = "none"
        var drmLicense = ""
        val headers = mutableMapOf<String, String>()

        if (input.contains("?|")) {
            val parts = input.split("?|")
            cleanUrl = parts[0]
            val params = parts[1].split("&")
            
            for (param in params) {
                val kv = param.split("=", limit = 2)
                if (kv.size == 2) {
                    when (kv[0]) {
                        "drmScheme" -> drmType = kv[1]
                        "drmLicense" -> drmLicense = kv[1]
                        else -> headers[kv[0]] = kv[1] 
                    }
                }
            }
        }

        if (drmType.equals("clearkey", true) && drmLicense.contains(":") && !drmLicense.startsWith("http")) {
            drmLicense = generateClearKeyUri(drmLicense)
        }

        return StreamConfig(cleanUrl, drmType, drmLicense, headers)
    }

    private fun generateClearKeyUri(kidKey: String): String {
        try {
            val parts = kidKey.split(":")
            val kidHex = parts[0].replace("-", "")
            val keyHex = parts[1].replace("-", "")

            val kidBytes = kidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            val kidB64 = Base64.encodeToString(kidBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val keyB64 = Base64.encodeToString(keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

            val keyObj = JSONObject().apply {
                put("kty", "oct")
                put("k", keyB64)
                put("kid", kidB64)
            }
            val keysArr = JSONArray().put(keyObj)
            val root = JSONObject().apply {
                put("keys", keysArr)
                put("type", "temporary")
            }

            val jsonBytes = root.toString().toByteArray()
            val dataB64 = Base64.encodeToString(jsonBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            return "data:application/json;base64,$dataB64"
        } catch (e: Exception) {
            return kidKey
        }
    }
}
