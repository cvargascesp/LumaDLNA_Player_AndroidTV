package cl.androiddlna

import org.junit.Assert.*
import org.junit.Test

class ServerIdentityTest {
    private val uuid = "1c4f9863-5aff-be23-a966-69a144f7ea1c"
    @Test fun multipleInterfacesAreOneDevice() {
        assertEquals(serverIdentity("uuid:$uuid", "http://192.168.1.2/control", "http://192.168.1.2/device"), serverIdentity("uuid:$uuid", "http://172.31.48.1/control", "http://172.31.48.1/device"))
    }
    @Test fun oldPlexCacheMatchesNewDiscovery() {
        assertEquals(serverIdentity("", "http://172.31.48.1/ContentDirectory/$uuid/control.xml", "http://172.31.48.1/device"), serverIdentity("uuid:$uuid", "http://192.168.1.2/control", "http://192.168.1.2/device"))
    }
    @Test fun distinctDevicesStaySeparate() {
        assertNotEquals(serverIdentity("uuid:first", "http://a/control", "http://a/device"), serverIdentity("uuid:second", "http://b/control", "http://b/device"))
    }
    @Test fun unknownDevicesUseAddress() {
        assertNotEquals(serverIdentity("", "http://a/control", "http://a/device"), serverIdentity("", "http://b/control", "http://b/device"))
    }
}
