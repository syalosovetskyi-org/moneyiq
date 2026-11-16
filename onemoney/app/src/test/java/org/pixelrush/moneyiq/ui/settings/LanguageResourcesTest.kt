package org.syalosovetskyi.onemoney.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.syalosovetskyi.onemoney.ui.settings.data.LANGUAGE_DEFAULT
import org.syalosovetskyi.onemoney.ui.settings.data.languageOptions
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * #3037 R12: діалог «Мова» пропонує лише мови, для яких є переклад.
 * Список R.array.app_language_tags має збігатися з каталогами res/values-xx/strings.xml
 * (+ uk — базова values/).
 */
class LanguageResourcesTest {

    private val resDir: File = listOf("src/main/res", "app/src/main/res")
        .map(::File).firstOrNull { it.isDirectory }
        ?: error("res directory not found from ${File(".").absolutePath}")

    private fun declaredTags(): List<String> {
        val file = File(resDir, "values/languages.xml")
        assertTrue("missing ${file.path}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val arrays = doc.getElementsByTagName("string-array")
        for (i in 0 until arrays.length) {
            val node = arrays.item(i) as org.w3c.dom.Element
            if (node.getAttribute("name") == "app_language_tags") {
                val items = node.getElementsByTagName("item")
                return (0 until items.length).map { items.item(it).textContent.trim() }
            }
        }
        error("app_language_tags not found in ${file.path}")
    }

    private fun translatedTags(): Set<String> {
        val pattern = Regex("""values-([a-z]{2,3})(-r[A-Z]{2})?""")
        val translated = resDir.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").isFile }
            .mapNotNull { pattern.matchEntire(it.name)?.groupValues?.get(1) }
            .toSet()
        return translated + "uk"   // values/ — українська (базова локаль)
    }

    @Test
    fun `language list matches translated resource folders`() {
        assertEquals(translatedTags(), declaredTags().toSet())
    }

    @Test
    fun `untranslated languages are not offered`() {
        val offered = languageOptions(declaredTags()).map { it.first }
        assertEquals(LANGUAGE_DEFAULT, offered.first())
        for (tag in listOf("de", "fr", "pl")) {
            if (tag !in translatedTags()) assertTrue("$tag offered without translation", tag !in offered)
        }
    }
}
