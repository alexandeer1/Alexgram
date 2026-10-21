package xyz.nextalone.nagram.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tw.nekomimi.nekogram.utils.HttpClient
import java.io.IOException

class ContentType(val value: String) {
    override fun toString(): String = value

    companion object {
        val Application = ApplicationTypes

        @JvmStatic
        fun parse(value: String): ContentType = ContentType(value)
    }

    object ApplicationTypes {
        val Json = ContentType("application/json")
    }
}

data class HttpStatusCode(val value: Int, val description: String = "") {
    companion object {
        val OK = HttpStatusCode(200, "OK")
        val TooManyRequests = HttpStatusCode(429, "Too Many Requests")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpStatusCode) return false
        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}

data class NetworkResponse(
    val statusCode: Int,
    val body: String,
    val bodyBytes: ByteArray? = null,
    val headers: String = "",
    val responseTime: Long = 0,
    val isSuccess: Boolean = statusCode in 200..299,
    val error: String? = null
) {
    fun status(): HttpStatusCode = HttpStatusCode(statusCode, "")

    fun isSuccessful(): Boolean = isSuccess

    fun isRedirect(): Boolean = statusCode in 300..399

    fun isClientError(): Boolean = statusCode in 400..499

    fun isServerError(): Boolean = statusCode in 500..599

    fun header(name: String): String? {
        return headers.lines()
            .find { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
    }

    fun bodyAsBytes(): ByteArray? = bodyBytes
}

class NetworkRequestBuilder(
    private var url: String,
    private var method: String = "GET",
    private val headersMap: MutableMap<String, String> = mutableMapOf(),
    private val paramsMap: MutableMap<String, String> = mutableMapOf(),
    private var contentBody: String? = null,
    private var binaryBody: ByteArray? = null,
    private var contentType: ContentType? = null
) {
    private var onCompleteCallback: (suspend () -> Unit)? = null

    fun url(url: String): NetworkRequestBuilder {
        this.url = url
        return this
    }

    fun method(method: String): NetworkRequestBuilder {
        this.method = method
        return this
    }

    fun header(key: String, value: String): NetworkRequestBuilder {
        headersMap[key] = value
        return this
    }

    fun headers(block: MutableMap<String, String>.() -> Unit): NetworkRequestBuilder {
        headersMap.block()
        return this
    }

    fun parameter(key: String, value: String): NetworkRequestBuilder {
        paramsMap[key] = value
        return this
    }

    fun parameters(block: MutableMap<String, String>.() -> Unit): NetworkRequestBuilder {
        paramsMap.block()
        return this
    }

    fun setBody(body: String): NetworkRequestBuilder {
        this.contentBody = body
        this.binaryBody = null
        return this
    }

    fun body(body: String): NetworkRequestBuilder {
        this.contentBody = body
        this.binaryBody = null
        return this
    }

    fun setBinaryBody(body: ByteArray): NetworkRequestBuilder {
        this.binaryBody = body
        this.contentBody = null
        return this
    }

    fun binaryBody(body: ByteArray): NetworkRequestBuilder {
        this.binaryBody = body
        this.contentBody = null
        return this
    }

    fun contentType(contentType: ContentType): NetworkRequestBuilder {
        this.contentType = contentType
        headersMap["Content-Type"] = contentType.toString()
        return this
    }

    fun onComplete(block: suspend () -> Unit): NetworkRequestBuilder {
        onCompleteCallback = block
        return this
    }

    fun execute(): NetworkResponse {
        val startTime = System.currentTimeMillis()
        var finalUrl = url
        if (paramsMap.isNotEmpty()) {
            val httpUrlBuilder = url.toHttpUrlOrNull()?.newBuilder()
            if (httpUrlBuilder != null) {
                paramsMap.forEach { (k, v) ->
                    httpUrlBuilder.addQueryParameter(k, v)
                }
                finalUrl = httpUrlBuilder.build().toString()
            } else {
                val separator = if (url.contains("?")) "&" else "?"
                val queryString = paramsMap.entries.joinToString("&") { "${it.key}=${it.value}" }
                finalUrl = url + separator + queryString
            }
        }

        val requestBuilder = Request.Builder().url(finalUrl)
        headersMap.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }

        val mediaTypeString = contentType?.value
            ?: headersMap["Content-Type"]
            ?: "application/json; charset=utf-8"
        val mediaType = mediaTypeString.toMediaTypeOrNull()

        val reqMethod = method.uppercase()
        when (reqMethod) {
            "GET" -> requestBuilder.get()
            "DELETE" -> requestBuilder.delete()
            "HEAD" -> requestBuilder.head()
            "POST", "PUT", "PATCH" -> {
                val requestBody = when {
                    binaryBody != null -> binaryBody!!.toRequestBody(mediaType)
                    contentBody != null -> contentBody!!.toRequestBody(mediaType)
                    else -> "".toRequestBody(mediaType)
                }
                when (reqMethod) {
                    "POST" -> requestBuilder.post(requestBody)
                    "PUT" -> requestBuilder.put(requestBody)
                    "PATCH" -> requestBuilder.patch(requestBody)
                }
            }
            else -> requestBuilder.method(reqMethod, null)
        }

        val client = HttpClient.llmInstance

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                val code = response.code
                val responseBytes = response.body?.bytes()
                val responseBody = responseBytes?.toString(Charsets.UTF_8) ?: ""
                val headersString = response.headers.joinToString("\n") { "${it.first}: ${it.second}" }

                return NetworkResponse(
                    statusCode = code,
                    body = responseBody,
                    bodyBytes = responseBytes,
                    headers = headersString,
                    responseTime = duration,
                    isSuccess = response.isSuccessful
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            return NetworkResponse(
                statusCode = 0,
                body = "",
                bodyBytes = null,
                headers = "",
                responseTime = duration,
                isSuccess = false,
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }

    suspend fun executeAsync(): NetworkResponse = withContext(Dispatchers.IO) {
        execute()
    }

    companion object {
        @JvmStatic
        fun get(url: String, block: NetworkRequestBuilder.() -> Unit = {}): NetworkRequestBuilder {
            return NetworkRequestBuilder(url, "GET").apply(block)
        }

        @JvmStatic
        fun post(url: String, block: NetworkRequestBuilder.() -> Unit = {}): NetworkRequestBuilder {
            return NetworkRequestBuilder(url, "POST").apply(block)
        }

        @JvmStatic
        fun put(url: String, block: NetworkRequestBuilder.() -> Unit = {}): NetworkRequestBuilder {
            return NetworkRequestBuilder(url, "PUT").apply(block)
        }

        @JvmStatic
        fun delete(url: String, block: NetworkRequestBuilder.() -> Unit = {}): NetworkRequestBuilder {
            return NetworkRequestBuilder(url, "DELETE").apply(block)
        }

        @JvmStatic
        fun patch(url: String, block: NetworkRequestBuilder.() -> Unit = {}): NetworkRequestBuilder {
            return NetworkRequestBuilder(url, "PATCH").apply(block)
        }

        @JvmStatic
        fun head(url: String, block: NetworkRequestBuilder.() -> Unit = {}): NetworkRequestBuilder {
            return NetworkRequestBuilder(url, "HEAD").apply(block)
        }

        @JvmStatic
        fun options(url: String, block: NetworkRequestBuilder.() -> Unit = {}): NetworkRequestBuilder {
            return NetworkRequestBuilder(url, "OPTIONS").apply(block)
        }
    }
}
