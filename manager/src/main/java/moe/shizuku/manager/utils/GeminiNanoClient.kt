package moe.shizuku.manager.utils

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device Gemini Nano client backed by the ML Kit GenAI Prompt API and Android AICore.
 *
 * All ML Kit calls are isolated behind [Api26Impl] so devices running Android 7.x (API 24/25)
 * never load ML Kit classes requiring API 26+.
 */
object GeminiNanoClient {

    enum class AvailabilityStatus {
        AVAILABLE,
        DOWNLOADABLE,
        DOWNLOADING,
        UNAVAILABLE,
        UNSUPPORTED_ANDROID,
    }

    data class Availability(
        val status: AvailabilityStatus,
        val baseModelName: String? = null,
        val errorMessage: String? = null,
    )

    suspend fun checkAvailability(): Availability = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext Availability(
                status = AvailabilityStatus.UNSUPPORTED_ANDROID,
                errorMessage = "Gemini Nano requires Android 8.0 (API 26) or higher.",
            )
        }
        Api26Impl.checkAvailability()
    }

    suspend fun downloadModel(
        onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext Result.failure(
                IllegalStateException("Gemini Nano requires Android 8.0 (API 26) or higher.")
            )
        }
        Api26Impl.downloadModel(onProgress)
    }

    suspend fun chatCompletion(
        systemPrompt: String?,
        userPrompt: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withContext Result.failure(
                IllegalStateException("Gemini Nano requires Android 8.0 (API 26) or higher.")
            )
        }
        Api26Impl.chatCompletion(systemPrompt, userPrompt)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private object Api26Impl {

        suspend fun checkAvailability(): Availability {
            val client = try {
                Generation.getClient()
            } catch (t: Throwable) {
                return Availability(
                    status = AvailabilityStatus.UNAVAILABLE,
                    errorMessage = formatError(t),
                )
            }
            return try {
                when (client.checkStatus()) {
                    FeatureStatus.AVAILABLE -> {
                        val modelName = runCatching { client.getBaseModelName() }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                        Availability(
                            status = AvailabilityStatus.AVAILABLE,
                            baseModelName = modelName,
                        )
                    }
                    FeatureStatus.DOWNLOADABLE -> Availability(
                        status = AvailabilityStatus.DOWNLOADABLE,
                    )
                    FeatureStatus.DOWNLOADING -> Availability(
                        status = AvailabilityStatus.DOWNLOADING,
                    )
                    else -> Availability(
                        status = AvailabilityStatus.UNAVAILABLE,
                        errorMessage = "Gemini Nano is unavailable on this device. Requires a supported Pixel (Pixel 9+) with Android AICore enabled.",
                    )
                }
            } catch (t: Throwable) {
                Availability(
                    status = AvailabilityStatus.UNAVAILABLE,
                    errorMessage = formatError(t),
                )
            } finally {
                runCatching { client.close() }
            }
        }

        suspend fun downloadModel(
            onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)?,
        ): Result<Unit> {
            val client = try {
                Generation.getClient()
            } catch (t: Throwable) {
                return Result.failure(IllegalStateException(formatError(t), t))
            }
            return try {
                when (client.checkStatus()) {
                    FeatureStatus.AVAILABLE -> Result.success(Unit)
                    FeatureStatus.UNAVAILABLE -> Result.failure(
                        IllegalStateException(
                            "Gemini Nano is unavailable on this device. Requires a supported Pixel (Pixel 9+) with Android AICore enabled."
                        )
                    )
                    else -> {
                        var totalBytes: Long? = null
                        var failure: Throwable? = null
                        client.download().collect { status ->
                            when (status) {
                                is DownloadStatus.DownloadStarted -> {
                                    totalBytes = status.bytesToDownload.takeIf { it > 0L }
                                    onProgress?.invoke(0L, totalBytes)
                                }
                                is DownloadStatus.DownloadProgress -> {
                                    onProgress?.invoke(status.totalBytesDownloaded, totalBytes)
                                }
                                is DownloadStatus.DownloadCompleted -> {
                                    // Completed cleanly
                                }
                                is DownloadStatus.DownloadFailed -> {
                                    failure = status.e
                                }
                            }
                        }
                        val err = failure
                        if (err != null) {
                            Result.failure(IllegalStateException(formatError(err), err))
                        } else {
                            Result.success(Unit)
                        }
                    }
                }
            } catch (t: Throwable) {
                Result.failure(IllegalStateException(formatError(t), t))
            } finally {
                runCatching { client.close() }
            }
        }

        suspend fun chatCompletion(
            systemPrompt: String?,
            userPrompt: String,
        ): Result<String> {
            val client = try {
                Generation.getClient()
            } catch (t: Throwable) {
                return Result.failure(IllegalStateException(formatError(t), t))
            }
            return try {
                when (client.checkStatus()) {
                    FeatureStatus.AVAILABLE -> Unit
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                        var downloadError: Throwable? = null
                        client.download().collect { status ->
                            if (status is DownloadStatus.DownloadFailed) {
                                downloadError = status.e
                            }
                        }
                        downloadError?.let { err ->
                            return Result.failure(IllegalStateException(formatError(err), err))
                        }
                        if (client.checkStatus() != FeatureStatus.AVAILABLE) {
                            return Result.failure(
                                IllegalStateException("Gemini Nano model is still downloading. Please try again in a moment.")
                            )
                        }
                    }
                    else -> {
                        return Result.failure(
                            IllegalStateException(
                                "Gemini Nano is unavailable on this device. Requires a supported Pixel (Pixel 9+) with Android AICore enabled."
                            )
                        )
                    }
                }

                val trimmedSystem = systemPrompt?.trim().orEmpty()
                val supportsSystemPrompt = trimmedSystem.isNotEmpty() &&
                    runCatching { client.isSystemPromptAvailable() }.getOrDefault(false)

                val requestBuilder = if (supportsSystemPrompt) {
                    GenerateContentRequest.Builder(
                        SystemInstruction(trimmedSystem),
                        TextPart(userPrompt),
                    )
                } else {
                    val combined = if (trimmedSystem.isNotEmpty()) {
                        "$trimmedSystem\n\n$userPrompt"
                    } else {
                        userPrompt
                    }
                    GenerateContentRequest.Builder(TextPart(combined))
                }

                val response = client.generateContent(requestBuilder.build())
                val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
                if (text.isEmpty()) {
                    Result.failure(IllegalStateException("Gemini Nano returned an empty response."))
                } else {
                    Result.success(text)
                }
            } catch (t: Throwable) {
                Result.failure(IllegalStateException(formatError(t), t))
            } finally {
                runCatching { client.close() }
            }
        }

        private fun formatError(t: Throwable): String {
            if (t is GenAiException) {
                val base = t.message?.takeIf { it.isNotBlank() } ?: "AICore error"
                return "$base (code ${t.errorCode})"
            }
            return t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        }
    }
}
