package cl.androiddlna

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.net.HttpURLConnection
import java.io.IOException

data class Server(val name: String, val location: String, val control: String, val service: String, val udn: String = "") {
    val identity: String get() = serverIdentity(udn, control, location)
    fun json() = JSONObject().put("name", name).put("location", location).put("control", control).put("service", service).put("udn", udn)
    companion object { fun from(j: JSONObject) = Server(j.getString("name"), j.getString("location"), j.getString("control"), j.getString("service"), j.optString("udn")) }
}
data class Entry(val id: String, val title: String, val folder: Boolean, val url: String = "", val art: String = "", val duration: String = "", val resolution: String = "", val mime: String = "", val size: String = "") {
    fun json() = JSONObject().put("id", id).put("title", title).put("folder", folder).put("url", url).put("art", art).put("duration", duration).put("resolution", resolution).put("mime", mime).put("size", size)
    companion object { fun from(j: JSONObject) = Entry(j.getString("id"), j.getString("title"), j.getBoolean("folder"), j.optString("url"), j.optString("art"), j.optString("duration"), j.optString("resolution"), j.optString("mime"), j.optString("size")) }
}
object Dlna {
    private fun request(url: String, body: String? = null, action: String = ""): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 2500; c.readTimeout = 5000
        try {
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                c.setRequestProperty("SOAPAction", "\"$action\"")
                val bytes = body.toByteArray(Charsets.UTF_8)
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            if (c.responseCode !in 200..299) {
                val fault = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val detail = runCatching {
                    val p = parser(fault); var description = ""
                    while (p.eventType != XmlPullParser.END_DOCUMENT) {
                        if (p.eventType == XmlPullParser.START_TAG && p.name in listOf("errorDescription", "faultstring")) description = p.nextText()
                        p.next()
                    }
                    description
                }.getOrDefault("")
                throw IOException("HTTP ${c.responseCode} ${detail.ifBlank { c.responseMessage.orEmpty() }}")
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
    private fun parser(text: String) = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true); setInput(text.reader()) }
    suspend fun discover(onServer: suspend (Server) -> Unit) = withContext(Dispatchers.IO) {
        val seen = mutableSetOf<String>()
        val devices = mutableSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 700
            val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n".toByteArray()
            repeat(2) { socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("239.255.255.250"), 1900)) }
            val until = System.currentTimeMillis() + 3500
            while (System.currentTimeMillis() < until) {
                val packet = DatagramPacket(ByteArray(65507), 65507)
                try { socket.receive(packet) } catch (_: java.net.SocketTimeoutException) { continue }
                val location = String(packet.data, 0, packet.length).lineSequence().firstOrNull { it.startsWith("location:", true) }?.substringAfter(':')?.trim() ?: continue
                if (!seen.add(location)) continue
                runCatching { describe(location) }.getOrNull()?.let { if (devices.add(it.identity)) onServer(it) }
            }
        }
    }
    private fun describe(location: String): Server {
        val p = parser(request(location)); var name = "Servidor DLNA"; var base = location; var udn = ""
        var service = ""; var control = ""; var found: Pair<String, String>? = null
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "friendlyName" -> name = p.nextText()
                "UDN" -> udn = p.nextText()
                "URLBase" -> base = p.nextText().ifBlank { location }
                "service" -> { service = ""; control = "" }
                "serviceType" -> service = p.nextText()
                "controlURL" -> control = p.nextText()
            }
            if (p.eventType == XmlPullParser.END_TAG && p.name == "service" && service.contains(":ContentDirectory:")) found = service to control
            p.next()
        }
        val pair = requireNotNull(found) { "Sin ContentDirectory" }
        return Server(name, location, URI(base).resolve(pair.second).toString(), pair.first, udn)
    }
    private fun escape(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    /** Read the actual hierarchy, including folders never opened on this device. */
    suspend fun folderMetadata(server: Server, id: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Browse xmlns:u="${server.service}"><ObjectID>${escape(id)}</ObjectID><BrowseFlag>BrowseMetadata</BrowseFlag><Filter>*</Filter><StartingIndex>0</StartingIndex><RequestedCount>0</RequestedCount><SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>"""
        val response = parser(request(server.control, body, "${server.service}#Browse"))
        var result = ""
        while (response.eventType != XmlPullParser.END_DOCUMENT) {
            if (response.eventType == XmlPullParser.START_TAG && response.name == "Result") result = response.nextText()
            response.next()
        }
        if (result.isBlank()) return@withContext null
        val metadata = parser(result)
        var parent: String? = null
        var title = ""
        while (metadata.eventType != XmlPullParser.END_DOCUMENT) {
            if (metadata.eventType == XmlPullParser.START_TAG) when (metadata.name) {
                "container" -> parent = metadata.getAttributeValue(null, "parentID")
                "title" -> title = metadata.nextText()
            }
            metadata.next()
        }
        parent?.let { it to title }
    }
    suspend fun browse(server: Server, id: String): List<Entry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<Entry>(); var start = 0
        do {
            // Plex rejects incomplete SOAP envelopes with HTTP 500 (Bad Request).
            val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:Browse xmlns:u="${server.service}"><ObjectID>${escape(id)}</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter><StartingIndex>$start</StartingIndex><RequestedCount>200</RequestedCount><SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>"""
            val p = parser(request(server.control, body, "${server.service}#Browse")); var result = ""; var count = 0; var total = 0
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG) when (p.name) { "Result" -> result = p.nextText(); "NumberReturned" -> count = p.nextText().toIntOrNull() ?: 0; "TotalMatches" -> total = p.nextText().toIntOrNull() ?: 0 }
                p.next()
            }
            if (result.isNotBlank()) entries += parseEntries(result, server.location)
            start += count
        } while (count > 0 && start < total)
        entries
    }
    private fun parseEntries(xml: String, base: String): List<Entry> {
        val p = parser(xml); val out = mutableListOf<Entry>(); var e: Entry? = null; var kind = ""
        fun resolve(s: String) = runCatching { URI(base).resolve(s).toString() }.getOrDefault(s)
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "container", "item" -> { e = Entry(p.getAttributeValue(null, "id") ?: "", "", p.name == "container"); kind = "" }
                "title" -> { val s = p.nextText(); e = e?.copy(title = s) }
                "class" -> kind = p.nextText()
                "albumArtURI" -> { val s = p.nextText(); e = e?.copy(art = resolve(s)) }
                "res" -> {
                    val protocol = p.getAttributeValue(null, "protocolInfo").orEmpty()
                    val duration = p.getAttributeValue(null, "duration").orEmpty(); val resolution = p.getAttributeValue(null, "resolution").orEmpty(); val size = p.getAttributeValue(null, "size").orEmpty()
                    val url = p.nextText()
                    if (e?.url.isNullOrBlank() && url.startsWith("http", true) && !protocol.contains("image/")) e = e?.copy(url = resolve(url), duration = duration, resolution = resolution, mime = protocol.split(':').getOrNull(2).orEmpty(), size = size)
                }
            }
            if (p.eventType == XmlPullParser.END_TAG && (p.name == "container" || p.name == "item")) {
                e?.let { if (it.folder || (it.url.isNotBlank() && (kind.contains("videoItem") || it.mime.startsWith("video/")))) out += it }; e = null
            }
            p.next()
        }
        return out
    }
}
