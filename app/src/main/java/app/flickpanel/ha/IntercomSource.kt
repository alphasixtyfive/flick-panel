package app.flickpanel.ha

import app.flickpanel.config.validIntercomStream
import okhttp3.HttpUrl

private const val proxyPath = "/api/webrtc/ws"

internal fun signedIntercomUrl(server: HttpUrl, signed: String, stream: String): HttpUrl? {
    if (!validIntercomStream(stream) || !signed.startsWith("$proxyPath?")) return null
    val endpoint = server.resolve(proxyPath.removePrefix("/")) ?: return null
    val result = server.resolve(signed.removePrefix("/")) ?: return null
    if (!endpoint.sameOrigin(result) || result.encodedPath != endpoint.encodedPath || result.fragment != null) return null
    val signature = result.queryParameter("authSig")?.takeIf(String::isNotBlank) ?: return null
    return endpoint.newBuilder().addQueryParameter("authSig", signature).addQueryParameter("url", stream).build()
}
