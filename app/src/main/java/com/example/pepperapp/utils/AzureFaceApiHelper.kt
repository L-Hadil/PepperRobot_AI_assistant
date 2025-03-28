package com.example.pepperapp.utils

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object AzureFaceApiHelper {

    private const val SUBSCRIPTION_KEY = "022Ht9GheymKYBsd0T3PeNsEkz9lQaOv6zehN6TzaV8mPtacGSjoJQQJ99BCAC5T7U2XJ3w3AAAKACOGcYuV"
    private const val ENDPOINT = "https://hadil.cognitiveservices.azure.com/"
    private const val PERSON_GROUP_ID = "hadil"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    fun createPerson(name: String): String {
        Log.d("Azure", "👤 Création personne: nom=$name")

        val url = "$ENDPOINT/face/v1.0/persongroups/$PERSON_GROUP_ID/persons"

        val json = JSONObject().apply {
            put("name", name)
        }

        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            Log.d("Azure", "📥 Réponse création: $body")

            if (!response.isSuccessful) throw Exception("Erreur création personne Azure")

            val obj = JSONObject(body ?: throw Exception("Pas de réponse"))
            return obj.getString("personId")
        }
    }

    fun addFaceToPerson(personId: String, bitmap: Bitmap): Boolean {
        Log.d("Azure", "🖼️ Upload visage vers Azure: personId=$personId")

        val url = "$ENDPOINT/face/v1.0/persongroups/$PERSON_GROUP_ID/persons/$personId/persistedFaces"

        val imageBytes = bitmapToByteArray(bitmap)
        val body = imageBytes.toRequestBody("application/octet-stream".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY)
            .build()

        client.newCall(request).execute().use { response ->
            val res = response.body?.string()
            Log.d("Azure", "📥 Réponse upload visage: $res")
            return response.isSuccessful
        }
    }

    fun trainPersonGroup() {
        Log.d("Azure", "🚀 Lancement entraînement Azure")

        val url = "$ENDPOINT/face/v1.0/persongroups/$PERSON_GROUP_ID/train"

        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody())
            .addHeader("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY)
            .build()

        client.newCall(request).execute().use { response ->
            Log.d("Azure", "📥 Réponse entraînement: code=${response.code}")
            if (!response.isSuccessful) throw Exception("Erreur entraînement Azure")
        }
    }

    fun identifyFace(imageBytes: ByteArray, callback: (String?) -> Unit) {
        detectFace(imageBytes) { faceArray ->
            if (faceArray == null || faceArray.length() == 0) {
                Log.d("Azure", "❌ Aucun visage détecté pour identification")
                callback(null)
                return@detectFace
            }

            val faceId = faceArray.getJSONObject(0).getString("faceId")
            Log.d("Azure", "🔍 FaceID détecté: $faceId")

            val jsonBody = JSONObject().apply {
                put("personGroupId", PERSON_GROUP_ID)
                put("faceIds", JSONArray().put(faceId))
                put("maxNumOfCandidatesReturned", 1)
                put("confidenceThreshold", 0.5)
            }

            val request = Request.Builder()
                .url("$ENDPOINT/face/v1.0/identify")
                .addHeader("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Azure", "❌ Échec identification", e)
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseStr = response.body?.string()
                    Log.d("Azure", "📥 Réponse identification: $responseStr")

                    val result = JSONArray(responseStr)
                    if (result.length() == 0) {
                        callback(null)
                        return
                    }

                    val candidates = result.getJSONObject(0).getJSONArray("candidates")
                    if (candidates.length() == 0) {
                        callback(null)
                        return
                    }

                    val personId = candidates.getJSONObject(0).getString("personId")
                    Log.d("Azure", "🎯 Match trouvé: personId=$personId")
                    getPersonName(personId, callback)
                }
            })
        }
    }

    private fun getPersonName(personId: String, callback: (String?) -> Unit) {
        val request = Request.Builder()
            .url("$ENDPOINT/face/v1.0/persongroups/$PERSON_GROUP_ID/persons/$personId")
            .addHeader("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Azure", "❌ Échec récupération nom", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseStr = response.body?.string()
                val json = JSONObject(responseStr ?: "")
                val name = json.optString("name", null)
                Log.d("Azure", "✅ Visage identifié comme: $name")
                callback(name)
            }
        })
    }

    fun detectFace(imageBytes: ByteArray, callback: (JSONArray?) -> Unit) {
        val request = Request.Builder()
            .url("$ENDPOINT/face/v1.0/detect?returnFaceId=true")
            .addHeader("Ocp-Apim-Subscription-Key", SUBSCRIPTION_KEY)
            .post(imageBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Azure", "❌ Échec détection visage", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("Azure", "📥 Réponse détection visage: $responseBody")
                if (response.isSuccessful && responseBody != null) {
                    callback(JSONArray(responseBody))
                } else {
                    callback(null)
                }
            }
        })
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }
}
