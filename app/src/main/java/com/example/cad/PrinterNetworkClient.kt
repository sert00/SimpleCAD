package com.example.cad

import com.example.data.PrinterProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object PrinterNetworkClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads a generated 3MF, STL, or STEP file directly to a local or remote 3D printer server
     * (OctoPrint, Moonraker/Klipper, PrusaLink, or custom remote print webhook).
     */
    suspend fun sendFileToPrinter(
        printer: PrinterProfile,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        startPrintImmediately: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = printer.endpointUrl.trim().trimEnd('/')
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                return@withContext Result.failure(
                    IllegalArgumentException("L'URL stampante deve iniziare con http:// o https://")
                )
            }

            val uploadUrl = when (printer.protocol) {
                "OCTOPRINT" -> "$baseUrl/api/files/local"
                "MOONRAKER" -> "$baseUrl/server/files/upload"
                "PRUSALINK" -> "$baseUrl/api/v1/files/usb"
                else -> "$baseUrl/upload"
            }

            val fileBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .apply {
                    if (printer.protocol == "OCTOPRINT") {
                        addFormDataPart("select", "true")
                        addFormDataPart("print", if (startPrintImmediately) "true" else "false")
                    } else if (printer.protocol == "MOONRAKER") {
                        addFormDataPart("root", "gcodes")
                        addFormDataPart("print", if (startPrintImmediately) "true" else "false")
                    }
                }
                .build()

            val reqBuilder = Request.Builder()
                .url(uploadUrl)
                .post(multipart)

            if (printer.apiKey.isNotBlank()) {
                reqBuilder.addHeader("X-Api-Key", printer.apiKey.trim())
                reqBuilder.addHeader("Authorization", "Bearer ${printer.apiKey.trim()}")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("File $fileName inviato con successo a ${printer.name} (HTTP ${response.code})")
                } else {
                    Result.failure(
                        RuntimeException("Risposta stampante HTTP ${response.code}: ${response.message}")
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
