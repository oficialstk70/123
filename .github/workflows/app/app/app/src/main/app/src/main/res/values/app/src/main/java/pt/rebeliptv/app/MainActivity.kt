package pt.rebeliptv.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import pt.rebeliptv.app.model.Category
import pt.rebeliptv.app.model.Channel
import pt.rebeliptv.app.network.XtreamApi
import pt.rebeliptv.app.player.PlayerManager
import pt.rebeliptv.app.storage.SecureStorage
import pt.rebeliptv.app.ui.ChannelAdapter
import pt.rebeliptv.app.ui.ConfigDialog

class MainActivity : ComponentActivity() {

    private lateinit var searchInput: EditText
    private lateinit var settingsButton: ImageButton
    private lateinit var categoryContainer: LinearLayout
    private lateinit var channelRecyclerView: RecyclerView
    private lateinit var playerView: PlayerView
    private lateinit var emptyMessage: TextView

    private lateinit var storage: SecureStorage
    private lateinit var api: XtreamApi
    private lateinit var playerManager: PlayerManager
    private lateinit var channelAdapter: ChannelAdapter

    private var allChannels: List<Channel> = emptyList()
    private var categories: List<Category> = emptyList()
    private var selectedCategoryId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_main)

        storage = SecureStorage(this)
        api = XtreamApi()

        initialiseViews()
        initialiseRecyclerView()
        initialisePlayer()

        settingsButton.setOnClickListener {
            showConfiguration()
        }

        searchInput.setOnEditorActionListener { _, _, _ ->
            filterChannels()
            false
        }

        searchInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    filterChannels()
                }

                override fun afterTextChanged(
                    s: android.text.Editable?
                ) = Unit
            }
        )

        if (storage.hasConfiguration()) {
            loadXtream()
        } else {
            showEmpty(
                "📋 Adiciona o teu servidor IPTV para começar"
            )
        }
    }

    private fun initialiseViews() {
        searchInput = findViewById(R.id.searchInput)
        settingsButton = findViewById(R.id.settingsButton)
        categoryContainer = findViewById(R.id.categoryContainer)
        channelRecyclerView = findViewById(
            R.id.channelRecyclerView
        )
        playerView = findViewById(R.id.playerView)
        emptyMessage = findViewById(R.id.emptyMessage)
    }

    private fun initialiseRecyclerView() {
        channelAdapter = ChannelAdapter { channel ->
            playChannel(channel)
        }

        channelRecyclerView.layoutManager =
            LinearLayoutManager(this)

        channelRecyclerView.adapter = channelAdapter

        channelRecyclerView.setHasFixedSize(true)
    }

    private fun initialisePlayer() {
        playerManager = PlayerManager(
            context = this,
            playerView = playerView,
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun showConfiguration() {
        ConfigDialog(
            context = this,
            storage = storage
        ) {
            loadXtream()
        }.show()
    }

    private fun loadXtream() {
        val host = storage.getHost()
        val username = storage.getUsername()
        val password = storage.getPassword()

        if (
            host.isNullOrBlank() ||
            username.isNullOrBlank() ||
            password.isNullOrBlank()
        ) {
            showConfiguration()
            return
        }

        showLoading("A ligar ao servidor...")

        lifecycleScope.launch {
            val connectionResult = api.testConnection(
                host = host,
                username = username,
                password = password
            )

            if (connectionResult.isFailure) {
                val message =
                    connectionResult.exceptionOrNull()
                        ?.message
                        ?: "Servidor indisponível."

                showError(message)
                return@launch
            }

            val categoriesResult = api.getCategories(
                host = host,
                username = username,
                password = password
            )

            categories = categoriesResult.getOrDefault(
                emptyList()
            )

            val channelsResult = api.getChannels(
                host = host,
                username = username,
                password = password
            )

            if (channelsResult.isFailure) {
                val message =
                    channelsResult.exceptionOrNull()
                        ?.message
                        ?: "Erro ao carregar canais."

                showError(message)
                return@launch
            }

            allChannels = channelsResult.getOrDefault(
                emptyList()
            )

            if (allChannels.isEmpty()) {
                showError("Nenhum canal encontrado.")
                return@launch
            }

            buildCategoryButtons()

            selectedCategoryId = null

            showChannels()

            channelAdapter.submitList(allChannels)

            Toast.makeText(
                this@MainActivity,
                "✅ ${allChannels.size} canais carregados",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun buildCategoryButtons() {
        categoryContainer.removeAllViews()

        addCategoryButton(
            id = null,
            name = "Todos"
        )

        categories.forEach { category ->
            addCategoryButton(
                id = category.id,
                name = category.name
            )
        }
    }

    private fun addCategoryButton(
        id: String?,
        name: String
    ) {
        val button = TextView(this).apply {
            text = name
            textSize = 12f
            setTextColor(
                android.graphics.Color.WHITE
            )
            gravity = android.view.Gravity.CENTER
            setPadding(28, 8, 28, 8)

            setBackgroundResource(
                R.drawable.category_button_background
            )

            isFocusable = true
            isClickable = true

            setOnClickListener {
                selectedCategoryId = id

                updateCategorySelection()

                filterChannels()
            }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(4, 4, 4, 4)

        categoryContainer.addView(
            button,
            params
        )
    }

    private fun updateCategorySelection() {
        for (i in 0 until categoryContainer.childCount) {
            val child = categoryContainer.getChildAt(i)

            child.isSelected =
                if (i == 0) {
                    selectedCategoryId == null
                } else {
                    val categoryIndex = i - 1

                    categoryIndex in categories.indices &&
                        categories[categoryIndex].id ==
                        selectedCategoryId
                }
        }
    }

    private fun filterChannels() {
        val query = searchInput.text
            .toString()
            .trim()
            .lowercase()

        val filtered = allChannels.filter { channel ->

            val matchesSearch =
                query.isBlank() ||
                    channel.name
                        .lowercase()
                        .contains(query)

            val matchesCategory =
                selectedCategoryId == null ||
                    channel.categoryId ==
                    selectedCategoryId

            matchesSearch && matchesCategory
        }

        channelAdapter.submitList(filtered)

        if (filtered.isEmpty()) {
            emptyMessage.text =
                "Nenhum canal encontrado."
            emptyMessage.visibility = View.VISIBLE
        } else {
            emptyMessage.visibility = View.GONE
        }
    }

    private fun playChannel(channel: Channel) {
        playerView.visibility = View.VISIBLE
        channelRecyclerView.visibility = View.VISIBLE
        emptyMessage.visibility = View.GONE

        Toast.makeText(
            this,
            "▶️ ${channel.name}",
            Toast.LENGTH_SHORT
        ).show()

        playerManager.play(
            channel.streamUrl
        )
    }

    private fun showLoading(message: String) {
        emptyMessage.text = message
        emptyMessage.visibility = View.VISIBLE
        channelRecyclerView.visibility = View.GONE
        playerView.visibility = View.GONE
    }

    private fun showEmpty(message: String) {
        emptyMessage.text = message
        emptyMessage.visibility = View.VISIBLE
        channelRecyclerView.visibility = View.GONE
        playerView.visibility = View.GONE
    }

    private fun showChannels() {
        emptyMessage.visibility = View.GONE
        channelRecyclerView.visibility = View.VISIBLE
        playerView.visibility = View.GONE
    }

    private fun showError(message: String) {
        runOnUiThread {
            emptyMessage.text = "❌ $message"
            emptyMessage.visibility = View.VISIBLE
            channelRecyclerView.visibility = View.GONE
            playerView.visibility = View.GONE

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun enterFullscreen() {
        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).apply {
            hide(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
        }
    }

    fun exitFullscreen() {
        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).apply {
            show(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
        }

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onDestroy() {
        playerManager.release()
        super.onDestroy()
    }
}
