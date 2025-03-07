package org.wordpress.android.fluxc.network.rest.wpcom.media.wpv2

import android.content.Context
import com.android.volley.RequestQueue
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.MediaActionBuilder
import org.wordpress.android.fluxc.generated.UploadActionBuilder
import org.wordpress.android.fluxc.generated.endpoint.WPAPI
import org.wordpress.android.fluxc.model.MediaModel
import org.wordpress.android.fluxc.model.MediaModel.MediaUploadState.FAILED
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.store.MediaStore.MediaError
import org.wordpress.android.fluxc.store.MediaStore.MediaErrorType
import org.wordpress.android.fluxc.store.MediaStore.MediaErrorType.PARSE_ERROR
import org.wordpress.android.fluxc.store.MediaStore.MediaErrorType.REQUEST_TOO_LARGE
import org.wordpress.android.fluxc.store.MediaStore.ProgressPayload
import org.wordpress.android.fluxc.tools.CoroutineEngine
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.MEDIA
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WPV2MediaRestClient @Inject constructor(
    dispatcher: Dispatcher,
    private val coroutineEngine: CoroutineEngine,
    @Named("regular") private val okHttpClient: OkHttpClient,
    appContext: Context?,
    @Named("regular") requestQueue: RequestQueue,
    accessToken: AccessToken,
    userAgent: UserAgent
) : BaseWPComRestClient(appContext, dispatcher, requestQueue, accessToken, userAgent) {
    private val gson: Gson by lazy { Gson() }

    private val currentUploads = ConcurrentHashMap<Int, CoroutineScope>()

    fun uploadMedia(site: SiteModel, media: MediaModel) {
        coroutineEngine.launch(MEDIA, this, "Upload Media using WPCom's v2 API") {
            syncUploadMedia(site, media)
                    .onStart {
                        currentUploads[media.id] = this@launch
                    }
                    .onCompletion {
                        currentUploads.remove(media.id)
                    }
                    .collect { payload ->
                        mDispatcher.dispatch(UploadActionBuilder.newUploadedMediaAction(payload))
                    }
        }
    }

    fun cancelUpload(media: MediaModel) {
        currentUploads[media.id]?.let { scope ->
            scope.cancel()
            val payload = ProgressPayload(media, 0f, false, true)
            mDispatcher.dispatch(MediaActionBuilder.newCanceledMediaUploadAction(payload))
        }
    }

    private fun syncUploadMedia(site: SiteModel, media: MediaModel): Flow<ProgressPayload> {
        return callbackFlow {
            val url = WPAPI.media.getWPComUrl(site.siteId)
            val body = WPRestUploadRequestBody(media) { media, progress ->
                if (!isClosedForSend) {
                    val payload = ProgressPayload(media, progress, false, null)
                    try {
                        offer(payload)
                    } catch (e: CancellationException) {
                        // the flow has been cancelled
                    }
                }
            }

            val request = Request.Builder()
                    .url(url)
                    .post(body = body)
                    .header(WPComGsonRequest.REST_AUTHORIZATION_HEADER, "Bearer ${accessToken.get()}")
                    .build()

            val call = okHttpClient.newCall(request)

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // If the upload has been canceled, then ignore errors
                    if (!isClosedForSend) {
                        val message = "media upload failed: $e"
                        AppLog.w(MEDIA, message)
                        val error = MediaError.fromIOException(e)
                        error.logMessage = message
                        handleFailure(media, error)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (isClosedForSend) return
                    if (response.isSuccessful) {
                        try {
                            val res = gson.fromJson(response.body!!.string(), MediaWPRESTResponse::class.java)
                            val uploadedMedia = res.toMediaModel()
                            val payload = ProgressPayload(uploadedMedia, 1f, true, false)
                            try {
                                sendBlocking(payload)
                                close()
                            } catch (e: CancellationException) {
                                // the flow has been cancelled
                            }
                        } catch (e: JsonSyntaxException) {
                            AppLog.e(MEDIA, e)
                            val error = MediaError(PARSE_ERROR)
                            handleFailure(media, error)
                        } catch (e: NullPointerException) {
                            AppLog.e(MEDIA, e)
                            val error = MediaError(PARSE_ERROR)
                            handleFailure(media, error)
                        }
                    } else {
                        val error = response.parseError()
                        handleFailure(media, error)
                    }
                }
            })

            awaitClose {
                call.cancel()
            }
        }
    }

    private fun ProducerScope<ProgressPayload>.handleFailure(media: MediaModel, error: MediaError) {
        media.setUploadState(FAILED)
        val payload = ProgressPayload(media, 1f, false, error)
        try {
            sendBlocking(payload)
            close()
        } catch (e: CancellationException) {
            // the flow has been cancelled
        }
    }

    private fun Response.parseError(): MediaError {
        val mediaError = MediaError(MediaErrorType.fromHttpStatusCode(code))
        mediaError.statusCode = code
        mediaError.logMessage = message
        if (mediaError.type == REQUEST_TOO_LARGE) {
            // 413 (Request too large) errors are coming from the web server and are not an API response like the rest
            mediaError.message = message
            return mediaError
        }
        try {
            val responseBody = body
            if (responseBody == null) {
                AppLog.e(MEDIA, "error uploading media, response body was empty $this")
                mediaError.type = PARSE_ERROR
                return mediaError
            }
            val jsonBody = JSONObject(responseBody.string())
            jsonBody.optString("message").takeIf { it.isNotEmpty() }?.let {
                mediaError.message = it
            }
            jsonBody.optString("code").takeIf { it.isNotEmpty() }?.let {
                mediaError.logMessage = it
            }
        } catch (e: JSONException) {
            // no op
            mediaError.logMessage = e.message
        } catch (e: IOException) {
            mediaError.logMessage = e.message
        }
        return mediaError
    }
}
