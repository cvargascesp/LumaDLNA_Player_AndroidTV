package cl.androiddlna

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.TrackSelectionDialogBuilder
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.doOnNextLayout
import coil.load
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("luma", MODE_PRIVATE) }
    private val servers = linkedMapOf<String, Server>()
    private val path = mutableListOf<Pair<String, String>>()
    private var server: Server? = null
    private var entries = emptyList<Entry>()
    private var browsing: Job? = null
    private var discovery: Job? = null
    private val shortcutRepairs = mutableSetOf<String>()
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var progressJob: Job? = null
    private var playerView: PlayerView? = null
    private lateinit var grid: RecyclerView
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var drawer: View? = null
    private var drawerOpen = false
    private var drawerReturnFocus: View? = null
    private var drawerFirst: View? = null
    private var drawerServers: LinearLayout? = null
    private var shortcutAction: Button? = null
    private var seekPreview: TextView? = null
    private var seekJob: Job? = null
    private var controlsHideJob: Job? = null
    private var seekKey = 0
    private var seekTarget = 0L
    private var seekStarted = 0L
    private var seekGesture: SeekGesture? = null
    private var initialBodyFocus = true
    private var selectionJob: Job? = null
    private var selectionKey = 0
    private var selectionView: View? = null
    private var selectionHeld = false
    private var multicast: WifiManager.MulticastLock? = null
    private val ink = Color.rgb(233, 240, 251)
    private val mint = Color.rgb(103, 232, 202)
    private data class Tile(val title: String, val detail: String, val art: String = "", val symbol: String = "▶", val click: () -> Unit, val hold: (() -> Unit)? = null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (seekKey != 0) { finishSeek(false); return }
                if (playerView?.isControllerFullyVisible == true) { playerView?.hideController(); return }
                if (player != null) { closePlayer(); screen(); showEntries(); return }
                if (drawerOpen) { toggleDrawer(false); return }
                browsing?.cancel()
                if (path.size > 1) { path.removeAt(path.lastIndex); openFolder(path.last().first, path.last().second, false) }
                else if (server != null) { server = null; path.clear(); home() }
                else finish()
            }
        })
        runCatching { JSONArray(prefs.getString("servers", "[]")).let { a -> repeat(a.length()) { val s = Server.from(a.getJSONObject(it)); servers[s.identity] = s } } }
        home(); discover()
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(s: String, size: Float = 18f) = TextView(this).apply { text = s; textSize = size; setTextColor(ink) }
    private fun background(color: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat(); setStroke(dp(2), stroke) }
    private fun action(title: String, run: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 13f; letterSpacing = .02f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(ink); minHeight = dp(42); minimumHeight = dp(42); minWidth = 0; minimumWidth = 0
        setPadding(dp(16), dp(10), dp(16), dp(10)); stateListAnimator = null
        background = background(0xff1a2736.toInt(), 0xff2a3a4b.toInt())
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        setOnFocusChangeListener { _, focused ->
            background = background(if (focused) mint else 0xff1a2736.toInt(), if (focused) mint else 0xff2a3a4b.toInt())
            setTextColor(if (focused) 0xff09251f.toInt() else ink)
            animate().scaleX(if (focused) 1.025f else 1f).scaleY(if (focused) 1.025f else 1f).setDuration(120).start()
        }
        setOnClickListener { run() }
    }
    private fun displayName(s: Server) = s.name.removePrefix("Plex Media Server:").trim()
    private fun resolved(s: Server) = servers[s.identity] ?: s
    private fun menuHeading(title: String) = label(title, 11f).apply { letterSpacing = .14f; setTextColor(0xff8195a7.toInt()); setPadding(dp(12),dp(22),0,dp(8)) }
    private fun menuItem(title: String, subtitle: String = "", run: () -> Unit) = action(if (subtitle.isBlank()) title else "$title\n$subtitle", run).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(-1, dp(if (subtitle.isBlank()) 46 else 64)).apply { setMargins(0,dp(3),0,dp(3)) }
    }
    private fun screen() {
        initialBodyFocus = true
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(36), dp(20), dp(36), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TR_BL, intArrayOf(0xff19313c.toInt(),0xff0b1421.toInt(),0xff090f19.toInt()))
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(label("LUMA", 22f).apply { letterSpacing = .16f; setTextColor(mint); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(dp(116),dp(48)))
        bar.addView(action("☰  Bibliotecas") { toggleDrawer(!drawerOpen) })
        bar.addView(Space(this), LinearLayout.LayoutParams(0,1,1f))
        shortcutAction = server?.let { s ->
            action(if (savedShortcut(s, path.last().first) != null) "−  Eliminar acceso" else "＋  Guardar acceso") {
                val saved = savedShortcut(s, path.last().first)
                if (saved == null) saveShortcut() else {
                    removeShortcut(saved)
                    screen(); showEntries()
                }
                initialBodyFocus = false
                shortcutAction?.requestFocus()
            }.also { bar.addView(it) }
        }
        bar.addView(action(if (server == null) "↻  Buscar servidores" else "↻  Actualizar") { if (server == null) discover() else openFolder(path.last().first, path.last().second, false, true) })
        root.addView(bar)
        root.addView(label(if (server == null) "TU CINE EN CASA" else displayName(server!!) + "  /  " + path.dropLast(1).joinToString("  /  ") { it.second }, 11f).apply {
            letterSpacing = .14f; setTextColor(mint); setPadding(dp(4),dp(18),0,dp(4)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.START
        })
        root.addView(label(if (server == null) "Todo listo para darle play." else path.lastOrNull()?.second.orEmpty(), 32f).apply {
            typeface = Typeface.create("sans-serif-medium",Typeface.NORMAL); setPadding(dp(2),0,0,dp(6)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        status = label("", 12f).apply { setPadding(dp(4),0,0,0); setTextColor(Color.rgb(155, 173, 197)) }; root.addView(status)
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, if (resources.configuration.screenWidthDp >= 900) 4 else 3)
            clipToPadding = false; setPadding(0, dp(18), 0, dp(12))
            isFocusableInTouchMode = true
            // Reserve focus in the body while asynchronous data is arriving.
            requestFocus()
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(label("OK  Seleccionar       /       Mantén OK  Guardar acceso       /       ←  Bibliotecas       /       Atrás  Volver", 11f).apply { setTextColor(0xff8296a9.toInt()); setPadding(dp(4),dp(8),0,0) })
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        drawerOpen = false
        val shade = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()); visibility = View.GONE; isClickable = true; setOnClickListener { toggleDrawer(false) } }
        val panel = ScrollView(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xff182c38.toInt(),0xff101a28.toInt())); isClickable = true; elevation = dp(24).toFloat() }
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(26), dp(24), dp(24)) }
        menu.addView(label("LUMA", 24f).apply { letterSpacing = .16f; setTextColor(mint); setTypeface(null,Typeface.BOLD) })
        menu.addView(label("Tu biblioteca personal", 12f).apply { setTextColor(0xff94a8ba.toInt()); setPadding(0,dp(6),0,dp(22)) })
        val close = menuItem("‹  Cerrar menú") { toggleDrawer(false) }; menu.addView(close); drawerFirst = close
        menu.addView(menuItem("⌂  Inicio", "Servidores y accesos") { browsing?.cancel(); server = null; path.clear(); home() })
        menu.addView(menuHeading("ACCESOS DIRECTOS"))
        val shortcuts = JSONArray(prefs.getString("shortcuts", "[]"))
        if (shortcuts.length() == 0) menu.addView(label("Abre una carpeta y pulsa\nGuardar acceso para añadirla aquí.", 14f))
        repeat(shortcuts.length()) { i ->
            val j = shortcuts.getJSONObject(i)
            menu.addView(menuItem("★  ${shortcutTitle(j)}", displayName(resolved(Server.from(j.getJSONObject("server"))))) {
                server = resolved(Server.from(j.getJSONObject("server"))); path.clear(); openFolder(j.getString("id"), j.getString("title"))
            }.apply { setOnLongClickListener {
                removeShortcut(j)
                if (server == null) home() else { screen(); showEntries() }
                toggleDrawer(true); true
            } })
        }
        menu.addView(menuHeading("EN TU RED"))
        drawerServers = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        menu.addView(drawerServers)
        updateDrawerServers()
        panel.addView(menu)
        shade.addView(panel, FrameLayout.LayoutParams(dp(296), -1, Gravity.START))
        frame.addView(shade, FrameLayout.LayoutParams(-1, -1)); drawer = shade
        setContentView(frame)
    }
    private fun toggleDrawer(open: Boolean) {
        if (player != null) return
        if (open) drawerReturnFocus = currentFocus
        drawerOpen = open; drawer?.visibility = if (open) View.VISIBLE else View.GONE
        root.descendantFocusability = if (open) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (open) drawerFirst?.requestFocus() else (drawerReturnFocus?.takeIf { it.isAttachedToWindow } ?: grid).requestFocus()
    }
    private fun updateDrawerServers() {
        val container = drawerServers ?: return
        // Do not replace focused menu rows during discovery.
        if (container.hasFocus()) return
        container.removeAllViews()
        servers.values.forEach { s -> container.addView(menuItem(displayName(s), "Servidor DLNA") { server = s; path.clear(); openFolder("0", "Biblioteca") }) }
    }
    private fun home(refresh: Boolean = false) {
        if (!refresh) screen()
        val tiles = mutableListOf<Tile>()
        val shortcuts = JSONArray(prefs.getString("shortcuts", "[]"))
        repeat(shortcuts.length()) { i ->
            val j = shortcuts.getJSONObject(i); val s = resolved(Server.from(j.getJSONObject("server")))
            tiles += Tile(shortcutTitle(j), displayName(s), symbol = "★", click = { server = s; path.clear(); openFolder(j.getString("id"), j.getString("title")) }, hold = {
                removeShortcut(j); home()
            })
        }
        servers.values.forEach { s -> tiles += Tile(displayName(s), "Biblioteca en red · DLNA", symbol = "▣", click = { server = s; path.clear(); openFolder("0", "Biblioteca") }) }
        displayTiles(tiles)
        updateDrawerServers()
        status.text = if (servers.isEmpty()) "Buscando servidores en tu red local…" else "${servers.size} servidores · Accesos disponibles inmediatamente"
    }
    private fun discover() {
        if (discovery?.isActive == true) return
        discovery = lifecycleScope.launch {
            multicast = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).createMulticastLock("luma-discovery").apply { setReferenceCounted(false); acquire() }
            try {
                Dlna.discover { s -> withContext(Dispatchers.Main) {
                    servers[s.identity] = s
                    prefs.edit().putString("servers", JSONArray().apply { servers.values.forEach { put(it.json()) } }.toString()).apply()
                    if (server == null && player == null) home(refresh = true)
                } }
                if (server == null && player == null) status.text = if (servers.isEmpty()) "No hay servidores visibles. Comprueba que estén encendidos y en la misma red; pulsa Buscar servidores." else "${servers.size} servidores · Descubrimiento completado"
            } catch (e: Exception) { if (e is CancellationException) throw e; if (server == null && player == null) status.text = "No se pudo buscar: ${e.localizedMessage}" }
            finally { multicast?.let { if (it.isHeld) it.release() }; multicast = null }
        }
    }
    private fun cacheKey(s: Server, id: String) = "folder:${s.identity}:$id"
    private fun openFolder(id: String, title: String, push: Boolean = true, refresh: Boolean = false) {
        val s = server ?: return
        browsing?.cancel(); if (push) path += id to title
        screen(); entries = emptyList()
        if (!refresh) runCatching { JSONArray(prefs.getString(cacheKey(s,id), prefs.getString("folder:${s.location}:$id", "[]"))).let { a -> entries = List(a.length()) { Entry.from(a.getJSONObject(it)) } } }
        showEntries(); status.text = "Actualizando biblioteca…"
        browsing = lifecycleScope.launch {
            try {
                val fresh = Dlna.browse(s, id)
                entries = fresh
                prefs.edit().putString(cacheKey(s,id), JSONArray().apply { fresh.forEach { put(it.json()) } }.toString()).apply()
                if (player == null) showEntries()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.w("LumaDLNA", "Error al leer la biblioteca DLNA", e)
                if (player == null) status.text = "No se pudo actualizar: ${e.localizedMessage ?: e.javaClass.simpleName}. ${if (entries.isNotEmpty()) "Mostrando caché. " else ""}Pulsa Actualizar para reintentar."
            }
        }
    }
    private fun showEntries() {
        status.text = "${entries.count { it.folder }} carpetas · ${entries.count { !it.folder }} videos" + if (entries.isEmpty()) " · Carpeta vacía o sin videos compatibles" else ""
        displayTiles(entries.map { e -> Tile(e.title, if (e.folder) "Carpeta" else listOf(e.duration.substringBefore('.'), e.resolution, e.mime.removePrefix("video/")).filter { it.isNotBlank() }.joinToString(" · "), e.art, if (e.folder) "▰" else "▶", {
            if (e.folder) openFolder(e.id,e.title) else play(entries.filter { !it.folder }, e)
        }, if (e.folder) ({ saveShortcut(e.id,e.title) }) else null) })
    }
    private fun displayTiles(tiles: List<Tile>) {
        val body = grid
        val old = body.adapter as? Tiles
        val position = currentFocus?.let { body.findContainingViewHolder(it)?.bindingAdapterPosition }
        val selected = position?.takeIf { it >= 0 }?.let { old?.keyAt(it) }
        val shouldFocus = !drawerOpen && (initialBodyFocus || body.hasFocus())
        val adapter = Tiles(tiles)
        body.adapter = adapter
        if (shouldFocus && tiles.isNotEmpty()) {
            val target = selected?.let { key -> tiles.indexOfFirst { tileKey(it) == key }.takeIf { it >= 0 } } ?: 0
            body.scrollToPosition(target)
            body.doOnNextLayout {
                if (body === grid && body.isAttachedToWindow && player == null && !drawerOpen) {
                    body.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()
                    initialBodyFocus = false
                }
            }
            body.requestLayout()
        }
    }
    private fun tileKey(tile: Tile) = "${tile.symbol}:${tile.title}:${tile.detail}"
    private fun shortcutTitle(shortcut: JSONObject): String {
        val s = Server.from(shortcut.getJSONObject("server"))
        val title = shortcutProgramName(listOf(shortcut.optString("programTitle")))
            ?: shortcutProgramName(cachedFolderTitles(s, shortcut.getString("id"), shortcut.getString("title")))
        if (title == null) repairShortcutTitle(shortcut, resolved(s))
        return title ?: shortcut.getString("title")
    }
    private fun repairShortcutTitle(shortcut: JSONObject, s: Server) {
        val id = shortcut.getString("id")
        val repairKey = "${s.identity}:${s.control}:$id"
        if (!shortcutRepairs.add(repairKey)) return
        lifecycleScope.launch {
            try {
                var current = id
                val visited = mutableSetOf<String>()
                var name: String? = null
                while (current != "-1" && visited.size < 32 && visited.add(current)) {
                    val metadata = Dlna.folderMetadata(s, current) ?: break
                    name = shortcutProgramName(listOf(metadata.second))
                    if (name != null) break
                    current = metadata.first
                }
                if (name == null) return@launch
                // Reload before writing: the user may have removed a favorite while fetching.
                val saved = JSONArray(prefs.getString("shortcuts", "[]"))
                var changed = false
                repeat(saved.length()) { index ->
                    val item = saved.getJSONObject(index)
                    if (item.getString("id") == id && Server.from(item.getJSONObject("server")).identity == s.identity) {
                        item.put("programTitle", name)
                        changed = true
                    }
                }
                if (changed) {
                    prefs.edit().putString("shortcuts", saved.toString()).apply()
                    if (server == null && player == null && !drawerOpen) home()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                shortcutRepairs.remove(repairKey)
                android.util.Log.w("LumaDLNA", "No se pudo recuperar el nombre del favorito", e)
            }
        }
    }
    private fun cachedFolderTitles(s: Server, id: String, title: String): List<String> {
        val parents = mutableMapOf<String, Pair<String, String>>()
        val prefixes = listOf("folder:${s.identity}:", "folder:${s.location}:")
        prefs.all.forEach { (key, value) ->
            val prefix = prefixes.firstOrNull { key.startsWith(it) } ?: return@forEach
            runCatching {
                val items = JSONArray(value as String)
                repeat(items.length()) { index ->
                    val item = Entry.from(items.getJSONObject(index))
                    if (item.folder) parents[item.id] = key.removePrefix(prefix) to item.title
                }
            }
        }
        val titles = mutableListOf(title)
        val visited = mutableSetOf(id)
        var current = id
        while (true) {
            val parent = parents[current]?.first ?: break
            if (!visited.add(parent)) break
            parents[parent]?.second?.let { titles.add(0, it) }
            current = parent
        }
        return titles
    }
    private fun savedShortcut(s: Server, id: String): JSONObject? {
        val shortcuts = JSONArray(prefs.getString("shortcuts", "[]"))
        return (0 until shortcuts.length()).map { shortcuts.getJSONObject(it) }.firstOrNull {
            it.getString("id") == id && Server.from(it.getJSONObject("server")).identity == s.identity
        }
    }
    private fun saveShortcut(id: String = path.last().first, title: String = path.last().second) {
        val s = server ?: return; val a = JSONArray(prefs.getString("shortcuts", "[]"))
        val exists = (0 until a.length()).any { val j = a.getJSONObject(it); j.getString("id") == id && Server.from(j.getJSONObject("server")).identity == s.identity }
        val titles = cachedFolderTitles(s, id, title)
        val programTitle = shortcutProgramName(titles) ?: shortcutProgramName(path.map { it.second } + title) ?: title
        if (!exists) { a.put(JSONObject().put("server",s.json()).put("id",id).put("title",title).put("programTitle",programTitle)); prefs.edit().putString("shortcuts",a.toString()).apply() }
        screen(); showEntries()
        Toast.makeText(this, "Acceso guardado en Bibliotecas", Toast.LENGTH_SHORT).show()
    }
    private fun removeShortcut(shortcut: JSONObject) {
        val identity = Server.from(shortcut.getJSONObject("server")).identity
        val current = JSONArray(prefs.getString("shortcuts", "[]"))
        val remaining = JSONArray()
        repeat(current.length()) { index ->
            val item = current.getJSONObject(index)
            if (item.getString("id") != shortcut.getString("id") ||
                Server.from(item.getJSONObject("server")).identity != identity) remaining.put(item)
        }
        prefs.edit().putString("shortcuts", remaining.toString()).apply()
        Toast.makeText(this, "Acceso eliminado", Toast.LENGTH_SHORT).show()
    }
    private fun play(queue: List<Entry>, selected: Entry) {
        browsing?.cancel()
        val p = ExoPlayer.Builder(this).build(); player = p
        val frame = FrameLayout(this)
        val view = PlayerView(this).apply { player = p; controllerShowTimeoutMs = 5000; setShowSubtitleButton(true); setShowNextButton(true); setShowPreviousButton(true); keepScreenOn = true }
        playerView = view; frame.addView(view, FrameLayout.LayoutParams(-1,-1))
        val timebar = view.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
        // Media3 periodically publishes the actual playback position. Keep the UI on
        // the selected destination until the D-pad gesture is committed or cancelled.
        view.findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
            .setProgressUpdateListener { _, _ ->
                if (seekKey != 0) showSeekPosition(seekTarget)
            }
        timebar.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setPlayedColor(Color.WHITE)
            background = null
            var pointerOver = false
            fun updateScrubber() = setScrubberColor(if (hasFocus() || pointerOver) mint else Color.WHITE)
            updateScrubber()
            contentDescription = "Posición del video. Izquierda y derecha para buscar; arriba para controles; abajo para audio y subtítulos."
            setOnFocusChangeListener { _, _ -> updateScrubber() }
            setOnHoverListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> pointerOver = true
                    MotionEvent.ACTION_HOVER_EXIT -> pointerOver = false
                }
                updateScrubber()
                false
            }
        }
        seekPreview = label("", 26f).apply { gravity = Gravity.CENTER; setPadding(dp(24), dp(16), dp(24), dp(16)); background = background(Color.rgb(18,29,47)); visibility = View.GONE }
        frame.addView(seekPreview, FrameLayout.LayoutParams(-2,-2,Gravity.CENTER))
        val tracks = LinearLayout(this).apply { gravity = Gravity.END }
        tracks.addView(action("Audio") { TrackSelectionDialogBuilder(this, "Audio", p, androidx.media3.common.C.TRACK_TYPE_AUDIO).build().show() })
        tracks.addView(action("Subtítulos") { TrackSelectionDialogBuilder(this, "Subtítulos", p, androidx.media3.common.C.TRACK_TYPE_TEXT).setShowDisableOption(true).build().show() })
        val audio = tracks.getChildAt(0).apply { id = View.generateViewId() }
        val subtitles = tracks.getChildAt(1).apply { id = View.generateViewId() }
        val playPause = view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
        timebar.nextFocusUpId = playPause.id
        timebar.nextFocusDownId = audio.id
        timebar.nextFocusForwardId = audio.id
        audio.nextFocusLeftId = audio.id
        audio.nextFocusRightId = subtitles.id
        audio.nextFocusForwardId = subtitles.id
        subtitles.nextFocusLeftId = audio.id
        val bottomControls = view.findViewById<LinearLayout>(androidx.media3.ui.R.id.exo_basic_controls)
        bottomControls.addView(tracks, 0, LinearLayout.LayoutParams(-2, dp(50)))
        bottomControls.getFocusables(View.FOCUS_FORWARD).forEach { control ->
            control.nextFocusUpId = timebar.id
            control.nextFocusDownId = control.id
        }
        view.findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_center_controls)
            .getFocusables(View.FOCUS_FORWARD).forEach { control ->
                control.nextFocusDownId = timebar.id
            }
        view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            tracks.visibility = visibility
            controlsHideJob?.cancel()
            if (visibility == View.VISIBLE) resetControlsTimeout()
            if (visibility == View.VISIBLE) timebar.requestFocus()
        })
        setContentView(frame)
        session = MediaSession.Builder(this,p).build()
        p.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                val oldItem = oldPosition.mediaItem ?: return
                val changed = oldPosition.mediaItemIndex != newPosition.mediaItemIndex
                val completed = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                if (changed || completed) saveProgress(oldItem.mediaId, oldPosition.positionMs, completed = completed)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                finishSeek(false)
                if (mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    p.seekTo(prefs.getLong(mediaItem.mediaId, 0L))
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) saveCurrentProgress()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { saveCurrentProgress() }
            override fun onPlayerError(error: PlaybackException) {
                saveCurrentProgress()
                Toast.makeText(this@MainActivity, "No se pudo reproducir. OK en reproducir para reintentar; Siguiente para continuar.", Toast.LENGTH_LONG).show(); view.showController()
            }
        })
        val serverId = requireNotNull(server).identity
        fun progressKey(e: Entry) = "progress:${serverId.length}:$serverId:${e.id}"
        p.setMediaItems(queue.map { e -> MediaItem.Builder().setMediaId(progressKey(e)).setUri(e.url).setMediaMetadata(MediaMetadata.Builder().setTitle(e.title).build()).build() }, queue.indexOf(selected), prefs.getLong(progressKey(selected), 0L))
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.prepare(); p.play(); view.showController(); timebar.requestFocus()
        progressJob = lifecycleScope.launch {
            while (isActive) { delay(1000); saveCurrentProgress() }
        }
    }
    private fun saveProgress(key: String, position: Long, duration: Long = -1L, completed: Boolean = false) {
        if (key.isBlank()) return
        val editor = prefs.edit()
        if (completed || (duration > 0 && position >= duration)) editor.remove(key)
        else editor.putLong(key, position.coerceAtLeast(0L))
        editor.apply()
    }
    private fun saveCurrentProgress() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        saveProgress(item.mediaId, p.currentPosition, p.duration, p.playbackState == Player.STATE_ENDED)
    }
    private fun closePlayer() { finishSeek(false); saveCurrentProgress(); progressJob?.cancel(); progressJob = null; controlsHideJob?.cancel(); controlsHideJob = null; seekPreview = null; playerView?.player = null; session?.release(); session = null; player?.release(); player = null; playerView = null }
    override fun onStop() { cancelSelection(); finishSeek(false); super.onStop(); player?.pause(); saveCurrentProgress() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (!hasFocus) { cancelSelection(); finishSeek(false) } }
    override fun onDestroy() { closePlayer(); discovery?.cancel(); multicast?.let { if (it.isHeld) it.release() }; super.onDestroy() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Own the complete OK gesture: some TV remotes never send a long-press repeat.
        // Consume release even when the long click rebuilt the screen.
        if (event.keyCode == selectionKey && selectionKey != 0) {
            if (event.action == KeyEvent.ACTION_UP) {
                val target = selectionView
                val click = !selectionHeld && !event.isCanceled && target?.isAttachedToWindow == true && target.hasFocus()
                cancelSelection()
                if (click) target?.performClick()
            }
            return true
        }
        if (player == null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            val target = currentFocus
            if (target?.isLongClickable == true) {
                cancelSelection()
                selectionKey = event.keyCode; selectionView = target
                selectionJob = lifecycleScope.launch {
                    delay(android.view.ViewConfiguration.getLongPressTimeout().toLong())
                    if (target.isAttachedToWindow && target.hasFocus()) selectionHeld = target.performLongClick()
                }
                return true
            }
        }
        if (playerView?.isControllerFullyVisible == true) resetControlsTimeout()
        if (player == null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU) { toggleDrawer(!drawerOpen); return true }
            if (drawerOpen && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { toggleDrawer(false); return true }
            if (!drawerOpen && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                val item = currentFocus?.let { grid.findContainingViewHolder(it) }
                val columns = (grid.layoutManager as GridLayoutManager).spanCount
                if (item != null && item.bindingAdapterPosition % columns == 0) { toggleDrawer(true); return true }
            }
        }
        val p = player
        if (p != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN) &&
            playerView?.isControllerFullyVisible == false) {
            playerView?.showController()
            playerView?.findViewById<View>(androidx.media3.ui.R.id.exo_progress)?.requestFocus()
            return true
        }
        if (p != null && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (event.action == KeyEvent.ACTION_UP && event.keyCode == seekKey) { finishSeek(!event.isCanceled); return true }
            val timebar = playerView?.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
            if (event.action == KeyEvent.ACTION_DOWN && (seekKey != 0 || playerView?.isControllerFullyVisible == false || timebar?.hasFocus() == true)) {
                if (seekKey == 0 && p.isCurrentMediaItemSeekable && p.duration > 0) {
                    playerView?.showController()
                    timebar?.requestFocus()
                    seekKey = event.keyCode; seekStarted = android.os.SystemClock.uptimeMillis(); seekTarget = p.currentPosition
                    seekGesture = SeekGesture(seekTarget, p.duration, if (seekKey == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1)
                    moveSeek()
                    seekJob = lifecycleScope.launch { delay(450); while (isActive) { moveSeek(); delay(180) } }
                }
                return true
            }
        }
        if (player != null && event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            player?.let { if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && it.isPlaying)) it.pause() else { if (it.playerError != null) it.prepare(); it.play() } }; return true
        }
        return super.dispatchKeyEvent(event)
    }
    private fun cancelSelection() {
        selectionJob?.cancel(); selectionJob = null
        selectionKey = 0; selectionView = null; selectionHeld = false
    }
    private fun moveSeek() {
        val p = player ?: return
        playerView?.showController()
        resetControlsTimeout()
        val held = android.os.SystemClock.uptimeMillis() - seekStarted
        seekTarget = seekGesture?.advance(held) ?: return
        showSeekPosition(seekTarget)
        fun time(ms: Long): String { val seconds = ms / 1000; return "%d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60) }
        seekPreview?.apply { text = "${if (seekKey == KeyEvent.KEYCODE_DPAD_RIGHT) "▶▶" else "◀◀"}  ${time(seekTarget)} / ${time(p.duration)}"; visibility = View.VISIBLE }
    }
    private fun finishSeek(commit: Boolean) {
        seekJob?.cancel(); seekJob = null
        val wasSeeking = seekKey != 0
        seekKey = 0; seekGesture = null; seekPreview?.visibility = View.GONE
        if (wasSeeking) {
            if (commit) player?.seekTo(seekTarget)
            showSeekPosition(player?.currentPosition ?: 0L)
        }
        if (playerView?.isControllerFullyVisible == true) resetControlsTimeout()
    }
    private fun showSeekPosition(position: Long) {
        playerView?.findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
            ?.setPosition(position)
        playerView?.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)
            ?.text = android.text.format.DateUtils.formatElapsedTime(position.coerceAtLeast(0) / 1000)
    }
    private fun resetControlsTimeout() {
        controlsHideJob?.cancel()
        controlsHideJob = lifecycleScope.launch {
            delay(5000)
            playerView?.hideController()
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        if (playerView?.isControllerFullyVisible == true) resetControlsTimeout()
        return handled
    }
    private inner class Tiles(private val tiles: List<Tile>) : RecyclerView.Adapter<Tiles.Holder>() {
        fun keyAt(position: Int) = tiles.getOrNull(position)?.let { tileKey(it) }
        inner class Holder(val box: LinearLayout, val image: ImageView, val name: TextView, val detail: TextView, val category: TextView) : RecyclerView.ViewHolder(box)
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
            val box = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true; clipToOutline = true
                setPadding(dp(2),dp(2),dp(2),dp(12))
                minimumHeight = dp(204)
                layoutParams = RecyclerView.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(6),dp(6),dp(6),dp(8)) }
                background = background(0xff152231.toInt(),0xff253545.toInt())
            }
            val image = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            val category = label("",9f).apply { letterSpacing = .12f; setTextColor(mint); setPadding(dp(14),dp(12),dp(14),dp(4)) }
            val name = label("",16f).apply { minLines = 2; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; typeface = Typeface.create("sans-serif-medium",Typeface.NORMAL); setPadding(dp(14),0,dp(12),0) }
            val detail = label("",11f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setTextColor(0xff94a8ba.toInt()); setPadding(dp(14),dp(5),dp(12),0) }
            box.addView(image,LinearLayout.LayoutParams(-1,dp(108))); box.addView(category); box.addView(name); box.addView(detail)
            box.setOnFocusChangeListener { _, focused ->
                box.background = background(if (focused) 0xff223d48.toInt() else 0xff152231.toInt(), if (focused) mint else 0xff253545.toInt())
                box.elevation = dp(if (focused) 12 else 0).toFloat()
                box.animate().scaleX(if (focused) 1.035f else 1f).scaleY(if (focused) 1.035f else 1f).setDuration(150).start()
            }
            return Holder(box,image,name,detail,category)
        }
        override fun getItemCount() = tiles.size
        override fun onBindViewHolder(h: Holder, position: Int) {
            val t = tiles[position]; h.name.text = t.title; h.detail.text = t.detail
            h.category.text = when (t.symbol) { "★" -> "ACCESO DIRECTO  ↗"; "▣" -> "SERVIDOR  /  DLNA"; "▰" -> "CARPETA  ↗"; else -> "VIDEO  /  REPRODUCIR" }
            h.image.load(t.art.ifBlank { null }) { placeholder(LibraryArtwork(t.symbol)); fallback(LibraryArtwork(t.symbol)); error(LibraryArtwork(t.symbol)); crossfade(true) }
            h.box.contentDescription = "${t.title}, ${t.detail}"
            h.box.setOnClickListener { t.click() }
            h.box.setOnLongClickListener(if (t.hold != null) View.OnLongClickListener { t.hold.invoke(); true } else null)
            h.box.isLongClickable = t.hold != null
        }
    }
}
