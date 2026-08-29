package dev.kensa.plugin.intellij.gutter

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

/** Phase of the run that produced a bundle, mirroring kensa-core's MCP classification. */
enum class RunPhase { RUNNING, ABANDONED, COMPLETE }

/**
 * `run.json` as kensa-core 0.9.2 writes it into each output bundle: `startedAt` and `pid` when the
 * first Kensa test class starts, `finishedAt` once the whole report is on disk. Absent in bundles
 * written by earlier Kensa versions — callers must treat a null read as "no marker, behave as before".
 */
data class KensaRunMarker(
    @SerializedName("startedAt") val startedAt: String?,
    @SerializedName("pid") val pid: Long?,
    @SerializedName("finishedAt") val finishedAt: String?,
) {
    val isFinished: Boolean get() = !finishedAt.isNullOrEmpty()

    /**
     * `finishedAt` is checked before pid liveness on purpose: a bundle copied from another machine
     * carries a foreign pid, but if it finished it is complete regardless.
     */
    fun phase(isPidAlive: (Long) -> Boolean = ::defaultPidAlive): RunPhase = when {
        isFinished -> RunPhase.COMPLETE
        pid != null && isPidAlive(pid) -> RunPhase.RUNNING
        else -> RunPhase.ABANDONED
    }

    companion object {
        private val gson = Gson()

        fun read(bundleDir: File): KensaRunMarker? {
            val file = File(bundleDir, "run.json")
            if (!file.isFile) return null
            return try {
                gson.fromJson(file.readText(), KensaRunMarker::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun classesWritten(bundleDir: File): Int =
            File(bundleDir, "results").listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0

        private fun defaultPidAlive(pid: Long): Boolean =
            ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
    }
}
