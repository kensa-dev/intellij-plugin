package dev.kensa.plugin.intellij.console

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class KensaConsoleFilterProviderTest {

    private val projectFixture = projectFixture()

    @Test
    fun `non-marker line produces no result`() {
        val filter = KensaOutputFilter(projectFixture.get())
        assertNull(filter.applyFilter("just some test output\n", 22))
    }

    @Test
    fun `marker line alone produces no result but arms filter`() {
        val filter = KensaOutputFilter(projectFixture.get())
        assertNull(filter.applyFilter("Kensa Output :\n", 15))

        val pathLine = "/tmp/kensa-output/index.html\n"
        val totalLength = 15 + pathLine.length
        assertNotNull(filter.applyFilter(pathLine, totalLength))
    }

    @Test
    fun `hyperlink offsets cover the path line only`() {
        val filter = KensaOutputFilter(projectFixture.get())
        filter.applyFilter("Kensa Output :\n", 15)

        val pathLine = "/tmp/kensa-output/index.html\n"
        val totalLength = 15 + pathLine.length

        val result = filter.applyFilter(pathLine, totalLength)!!
        val items = result.resultItems
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(15, item.highlightStartOffset)
        assertEquals(totalLength, item.highlightEndOffset)
    }

    @Test
    fun `filter disarms after capturing path so next line is ignored`() {
        val filter = KensaOutputFilter(projectFixture.get())
        filter.applyFilter("Kensa Output :\n", 15)
        filter.applyFilter("/tmp/kensa-output/index.html\n", 44)

        assertNull(filter.applyFilter("Tests passed\n", 57))
    }

    @Test
    fun `blank line after marker keeps filter armed`() {
        val filter = KensaOutputFilter(projectFixture.get())
        filter.applyFilter("Kensa Output :\n", 15)
        assertNull(filter.applyFilter("\n", 16))

        val pathLine = "/tmp/kensa-output/index.html\n"
        val total = 16 + pathLine.length
        assertNotNull(filter.applyFilter(pathLine, total))
    }
}
