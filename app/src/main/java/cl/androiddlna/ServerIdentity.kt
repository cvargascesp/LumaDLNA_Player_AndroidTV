package cl.androiddlna

import java.net.URI
import java.util.Locale

internal fun serverIdentity(udn: String, control: String, location: String): String {
    val uuid = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    val value = udn.trim().ifBlank { uuid.find(URI(control).path.orEmpty())?.value.orEmpty() }
    return if (value.isNotBlank()) "uuid:" + value.removePrefix("uuid:").lowercase(Locale.ROOT)
    else URI(location).normalize().toString()
}
