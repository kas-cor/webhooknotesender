package com.kascorp.webhooknotesender.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookApi @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Sends a webhook request.
     * @return Result with success message or error
     */
    suspend fun send(
        url: String,
        jsonPayload: String,
        bearerToken: String?
    ): Result<String> {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")

            if (!bearerToken.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $bearerToken")
            }

            val body = jsonPayload.toRequestBody(jsonMediaType)
            val request = requestBuilder
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            when {
                response.isSuccessful -> {
                    Result.success(responseBody)
                }
                response.code in 400..499 && response.code != 408 && response.code != 429 -> {
                    // Client error (except timeout/rate limit) — no retry
                    Result.failure(
                        WebhookException(
                            message = "HTTP ${response.code}: ${response.message}",
                            code = response.code,
                            shouldRetry = false
                        )
                    )
                }
                else -> {
                    // 5xx, timeout (408), rate limit (429) — should retry
                    Result.failure(
                        WebhookException(
                            message = "HTTP ${response.code}: ${response.message}",
                            code = response.code,
                            shouldRetry = true
                        )
                    )
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(
                WebhookException(
                    message = "Timeout: ${e.message}",
                    code = -1,
                    shouldRetry = true
                )
            )
        } catch (e: java.net.UnknownHostException) {
            Result.failure(
                WebhookException(
                    message = "No internet connection: ${e.message}",
                    code = -1,
                    shouldRetry = true
                )
            )
        } catch (e: Exception) {
            Result.failure(
                WebhookException(
                    message = "Error: ${e.message}",
                    code = -1,
                    shouldRetry = true
                )
            )
        }
    }
}

class WebhookException(
    override val message: String,
    val code: Int,
    val shouldRetry: Boolean
) : Exception(message)
