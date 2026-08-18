package pt.rebeliptv.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import pt.rebeliptv.app.model.Category
import pt.rebeliptv.app.model.Channel
import java.util.concurrent.TimeUnit

class XtreamApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(
        host: String,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildApiUrl(host, username, password)

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Servidor indisponível.")
                }

                val body = response.body?.string()
                    ?: error("Resposta vazia do servidor.")

                val json = org.json.JSONObject(body)

                val userInfo = json.optJSONObject("user_info")
                    ?: error("Resposta inválida do servidor.")

                val auth = userInfo.optInt("auth", 0)

                if (auth != 1) {
                    error("Credenciais inválidas.")
                }
            }
        }
    }

    suspend fun getCategories(
        host: String,
        username: String,
        password: String
    ): Result<List<Category>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildApiUrl(
                host,
                username,
                password,
                "get_live_categories"
            )

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Erro ao carregar categorias.")
                }

                val body = response.body?.string()
                    ?: error("Resposta vazia.")

                val array = JSONArray(body)
                val categories = mutableListOf<Category>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)

                    val id = item.optString("category_id")
                    val name = item.optString(
                        "category_name",
                        "Sem categoria"
                    )

                    if (id.isNotBlank()) {
                        categories.add(
                            Category(
                                id = id,
                                name = name
                            )
                        )
                    }
                }

                categories
            }
        }
    }

    suspend fun getChannels(
        host: String,
        username: String,
        password: String
    ): Result<List<Channel>> = withContext(Dispatchers.IO) {
        runCatching {
            val categoriesResult = getCategories(
                host,
                username,
                password
            )

            val categoryMap = categoriesResult.getOrDefault(emptyList())
                .associateBy { it.id }

            val url = buildApiUrl(
                host,
                username,
                password,
                "get_live_streams"
            )

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Erro ao carregar canais.")
                }

                val body = response.body?.string()
                    ?: error("Resposta vazia.")

                val array = JSONArray(body)
                val channels = mutableListOf<Channel>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)

                    val streamId = item.optString("stream_id")

                    if (streamId.isBlank()) {
                        continue
                    }

                    val name = item.optString(
                        "name",
                        "Canal $streamId"
                    )

                    val logo = item.optString(
                        "stream_icon",
                        ""
                    ).ifBlank { null }

                    val categoryId = item.optString(
                        "category_id",
                        ""
                    ).ifBlank { null }

                    val categoryName =
                        categoryId?.let { categoryMap[it]?.name }
                            ?: "Geral"

                    val streamUrl = buildStreamUrl(
                        host,
                        username,
                        password,
                        streamId
                    )

                    channels.add(
                        Channel(
                            id = streamId,
                            name = name,
                            logoUrl = logo,
                            categoryId = categoryId,
                            categoryName = categoryName,
                            streamUrl = streamUrl
                        )
                    )
                }

                channels
            }
        }
    }

    private fun buildApiUrl(
        host: String,
        username: String,
        password: String,
        action: String? = null
    ): String {
        val cleanHost = host.trim().removeSuffix("/")

        val builder = "$cleanHost/player_api.php"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)

        if (action != null) {
            builder.addQueryParameter("action", action)
        }

        return builder.build().toString()
    }

    private fun buildStreamUrl(
        host: String,
        username: String,
        password: String,
        streamId: String
    ): String {
        val cleanHost = host.trim().removeSuffix("/")

        return "$cleanHost/live/" +
            "${username.encodeUrlPart()}/" +
            "${password.encodeUrlPart()}/" +
            "$streamId.m3u8"
    }

    private fun String.encodeUrlPart(): String {
        return java.net.URLEncoder.encode(
            this,
            Charsets.UTF_8.name()
        )
    }
}
