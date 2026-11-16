package com.cerimoniarios.app.auth

import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Exposes `window.CerimoniariosAndroid` to pages from the app's own origin only.
 * Protocol (JSON strings): request `{id, type}`, reply `{id, ok, idToken?, error?}`.
 * The web counterpart is web/src/lib/native-app.ts.
 */
class NativeBridge(private val activity: AppCompatActivity) {

    fun attach(webView: WebView, allowedOrigin: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(webView, OBJECT_NAME, setOf(allowedOrigin)) { _, message, _, isMainFrame, reply ->
            if (!isMainFrame) return@addWebMessageListener
            val request = runCatching { JSONObject(message.data.orEmpty()) }.getOrNull() ?: return@addWebMessageListener
            handle(request.optString("id"), request.optString("type"), reply)
        }
    }

    private fun handle(id: String, type: String, reply: JavaScriptReplyProxy) {
        activity.lifecycleScope.launch {
            val response = JSONObject().put("id", id)
            when (type) {
                "googleSignIn" -> when (val result = GoogleSignIn.idToken(activity)) {
                    is SignInResult.Success -> response.put("ok", true).put("idToken", result.idToken)
                    is SignInResult.Failure -> response.put("ok", false).put("error", result.code)
                }
                "signOut" -> {
                    GoogleSignIn.signOut(activity)
                    response.put("ok", true)
                }
                else -> response.put("ok", false).put("error", "unsupported")
            }
            reply.postMessage(response.toString())
        }
    }

    private companion object {
        const val OBJECT_NAME = "CerimoniariosAndroid"
    }
}
