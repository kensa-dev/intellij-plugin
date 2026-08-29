package dev.kensa.plugin.intellij.gutter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class KensaRunMarkerTest {

    private fun tempBundle(): File = Files.createTempDirectory("kensa-run-marker").toFile()

    @Test
    fun `reads an in-flight marker`() {
        val bundle = tempBundle()
        File(bundle, "run.json").writeText(
            """{"startedAt":"2026-08-27T10:15:30.00Z","pid":12345,"finishedAt":null}"""
        )

        val marker = KensaRunMarker.read(bundle)!!

        assertEquals("2026-08-27T10:15:30.00Z", marker.startedAt)
        assertEquals(12345L, marker.pid)
        assertNull(marker.finishedAt)
        assertFalse(marker.isFinished)
    }

    @Test
    fun `reads a finished marker`() {
        val bundle = tempBundle()
        File(bundle, "run.json").writeText(
            """{"startedAt":"2026-08-27T10:15:30.00Z","pid":12345,"finishedAt":"2026-08-27T10:16:02.00Z"}"""
        )

        val marker = KensaRunMarker.read(bundle)!!

        assertTrue(marker.isFinished)
        assertEquals(RunPhase.COMPLETE, marker.phase { true })
    }

    @Test
    fun `reads live counts from the marker`() {
        val bundle = tempBundle()
        File(bundle, "run.json").writeText(
            """{"startedAt":"2026-08-27T10:15:30.00Z","pid":12345,"finishedAt":null,"classes":12,"passed":40,"failed":1,"disabled":3}"""
        )

        val marker = KensaRunMarker.read(bundle)!!

        assertEquals(12, marker.classes)
        assertEquals(40, marker.passed)
        assertEquals(1, marker.failed)
        assertEquals(3, marker.disabled)
    }

    @Test
    fun `live counts are null when the marker has none`() {
        val bundle = tempBundle()
        File(bundle, "run.json").writeText(
            """{"startedAt":"2026-08-27T10:15:30.00Z","pid":12345,"finishedAt":null}"""
        )

        val marker = KensaRunMarker.read(bundle)!!

        assertNull(marker.classes)
        assertNull(marker.passed)
        assertNull(marker.failed)
        assertNull(marker.disabled)
    }

    @Test
    fun `returns null when run json is absent`() {
        assertNull(KensaRunMarker.read(tempBundle()))
    }

    @Test
    fun `returns null when run json is malformed`() {
        val bundle = tempBundle()
        File(bundle, "run.json").writeText("not json {")
        assertNull(KensaRunMarker.read(bundle))
    }

    @Test
    fun `unfinished marker with live pid is running`() {
        val marker = KensaRunMarker(startedAt = "2026-08-27T10:15:30.00Z", pid = 12345, finishedAt = null)
        assertEquals(RunPhase.RUNNING, marker.phase { true })
    }

    @Test
    fun `unfinished marker with dead pid is abandoned`() {
        val marker = KensaRunMarker(startedAt = "2026-08-27T10:15:30.00Z", pid = 12345, finishedAt = null)
        assertEquals(RunPhase.ABANDONED, marker.phase { false })
    }

    @Test
    fun `unfinished marker with no pid is abandoned`() {
        val marker = KensaRunMarker(startedAt = "2026-08-27T10:15:30.00Z", pid = null, finishedAt = null)
        assertEquals(RunPhase.ABANDONED, marker.phase { true })
    }

    @Test
    fun `finished marker wins over pid state`() {
        // A bundle copied from another machine has a foreign pid; if it finished, it is complete.
        val marker = KensaRunMarker(startedAt = "x", pid = 99999, finishedAt = "2026-08-27T10:16:02.00Z")
        assertEquals(RunPhase.COMPLETE, marker.phase { false })
    }

    @Test
    fun `default pid liveness recognises the current process`() {
        val marker = KensaRunMarker(startedAt = "x", pid = ProcessHandle.current().pid(), finishedAt = null)
        assertEquals(RunPhase.RUNNING, marker.phase())
    }

    @Test
    fun `counts written result classes`() {
        val bundle = tempBundle()
        File(bundle, "results").apply { mkdirs() }.also {
            File(it, "com.example.A.json").writeText("{}")
            File(it, "com.example.B.json").writeText("{}")
            File(it, "notes.txt").writeText("ignored")
        }
        assertEquals(2, KensaRunMarker.classesWritten(bundle))
    }

    @Test
    fun `counts zero when results dir is absent`() {
        assertEquals(0, KensaRunMarker.classesWritten(tempBundle()))
    }
}
