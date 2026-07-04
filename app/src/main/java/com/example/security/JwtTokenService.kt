package com.example.security

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

data class JwtPayload(
    val email: String,
    val role: String,
    val exp: Long
)

object JwtTokenService {
    private const val SECRET_KEY = "StudyControllerSuperSecretCryptoKeyForJWTValidationSignature"

    private fun base64UrlEncode(input: ByteArray): String {
        return Base64.encodeToString(input, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun base64UrlDecode(input: String): ByteArray {
        return Base64.decode(input, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun hmacSha256(data: String, secret: String): ByteArray {
        val hmacKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    fun generateToken(email: String, role: String, validityMs: Long = 3600000 * 24): String {
        val header = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }.toString()

        val payload = JSONObject().apply {
            put("email", email)
            put("role", role)
            put("exp", System.currentTimeMillis() + validityMs)
        }.toString()

        val headerEncoded = base64UrlEncode(header.toByteArray(StandardCharsets.UTF_8))
        val payloadEncoded = base64UrlEncode(payload.toByteArray(StandardCharsets.UTF_8))

        val signatureData = "$headerEncoded.$payloadEncoded"
        val signature = hmacSha256(signatureData, SECRET_KEY)
        val signatureEncoded = base64UrlEncode(signature)

        return "$headerEncoded.$payloadEncoded.$signatureEncoded"
    }

    fun validateToken(token: String): JwtPayload? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null

            val headerEncoded = parts[0]
            val payloadEncoded = parts[1]
            val signatureEncoded = parts[2]

            val signatureData = "$headerEncoded.$payloadEncoded"
            val expectedSignature = hmacSha256(signatureData, SECRET_KEY)
            val expectedSignatureEncoded = base64UrlEncode(expectedSignature)

            if (signatureEncoded != expectedSignatureEncoded) {
                return null
            }

            val payloadJson = String(base64UrlDecode(payloadEncoded), StandardCharsets.UTF_8)
            val jsonObject = JSONObject(payloadJson)
            val email = jsonObject.getString("email")
            val role = jsonObject.getString("role")
            val exp = jsonObject.getLong("exp")

            if (System.currentTimeMillis() > exp) {
                return null
            }

            JwtPayload(email, role, exp)
        } catch (e: Exception) {
            null
        }
    }
}
