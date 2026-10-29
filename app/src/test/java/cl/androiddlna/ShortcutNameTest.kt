package cl.androiddlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutNameTest {
    @Test fun skipsNavigationAndSeasonFolders() {
        assertEquals("Dragon Ball Z", shortcutProgramName(listOf("Biblioteca", "Video", "Dragon Ball Z", "By Folder")))
        assertEquals("Two and a half men", shortcutProgramName(listOf("TV Shows", "Two and a half men", "Season 02")))
        assertEquals("South park", shortcutProgramName(listOf("Series", "South park", "Temporada 3", "By Folder")))
    }
    @Test fun rejectsPreviouslySavedGenericTitles() {
        assertNull(shortcutProgramName(listOf("All episodes")))
        assertNull(shortcutProgramName(listOf("By Folder")))
        assertEquals("Dragon Ball Z", shortcutProgramName(listOf("Dragon Ball Z", "All Episodes")))
        assertEquals("South park", shortcutProgramName(listOf("South park", "All Seasons", "Season 01", "All episodes")))
    }
}
