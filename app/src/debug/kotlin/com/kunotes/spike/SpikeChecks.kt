/**
 * Phase 0 spikes that must run on a real Android runtime (not the JVM tests):
 * - Q3: open a vault key and a note locked by desktop KuNotes, and time scrypt.
 * - Q4: JGit sync between two "devices" through a local bare repository, with a conflict.
 *
 * Debug builds only. Each check returns a few lines of text for the spike screen.
 */
package com.kunotes.spike

import android.content.Context
import com.kunotes.core.FileVaultFs
import com.kunotes.core.GitSync
import com.kunotes.core.Lock
import com.kunotes.core.VaultPath
import org.eclipse.jgit.api.Git
import java.io.File
import kotlin.system.measureTimeMillis

object SpikeChecks {
    fun lockCompatibility(context: Context): List<String> {
        val vault = freshDir(context, "spike-lock")
        // Copy the desktop-made fixture out of the APK's assets into a normal folder.
        // (APK assets can't start with ".", so the key file is stored without its dot.)
        val assetToFile = mapOf("kunotes-lock.age" to ".kunotes-lock.age", "Secret.md.age" to "Secret.md.age")
        for ((asset, fileName) in assetToFile) {
            context.assets.open("spike/lock-fixture/$asset").use { input ->
                File(vault, fileName).outputStream().use { input.copyTo(it) }
            }
        }
        val fs = FileVaultFs(vault)
        val lines = mutableListOf<String>()
        lines += "max heap: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MiB"
        lines +=
            step("unlock desktop key (scrypt 2^14)") {
                val key = Lock.unlock(fs, "correct horse")
                val text = Lock.readNote(fs, key, VaultPath("Secret.md.age"))
                if (text == "# Secret\n\nAPI key: 🔑 sk-123\r\n") "note bytes match" else "text differs!"
            }
        // Which scrypt work factor can a phone afford for a new password? (2^n KiB of memory)
        for (factor in listOf(15, 16, 17, 18)) {
            val newFs = FileVaultFs(freshDir(context, "spike-lock-$factor"))
            lines +=
                step("create + unlock at 2^$factor") {
                    Lock.createPassword(newFs, "phone pass 1", workFactor = factor)
                    val unlockMillis = measureTimeMillis { Lock.unlock(newFs, "phone pass 1") }
                    "unlock $unlockMillis ms"
                }
        }
        return lines
    }

    fun gitSync(context: Context): List<String> {
        val base = freshDir(context, "spike-git")
        val remote = File(base, "remote.git")
        Git
            .init()
            .setBare(true)
            .setDirectory(remote)
            .setInitialBranch("main")
            .call()
            .close()
        val url = remote.toURI().toString()
        val phoneA = File(base, "a").apply { mkdirs() }
        val phoneB = File(base, "b").apply { mkdirs() }
        val lines = mutableListOf<String>()

        File(phoneA, "Plan.md").writeText("# Plan\n\nfrom A\n")
        GitSync.connect(phoneA, url, credentials = null)
        val first = GitSync.connect(phoneB, url, credentials = null)
        lines += "B got from A: ${first.changed.map { it.value }}"

        // Both phones edit the same note, then both sync.
        File(phoneA, "Plan.md").writeText("# Plan\n\nedited on A\n")
        File(phoneB, "Plan.md").writeText("# Plan\n\nedited on B\n")
        GitSync.sync(phoneA, credentials = null)
        val second = GitSync.sync(phoneB, credentials = null)
        lines += "conflict copies on B: ${second.conflicts.map { it.value }}"
        val kept =
            File(phoneB, "Plan.md")
                .readText()
                .trim()
                .lines()
                .last()
        lines += "B kept its own version: \"$kept\""
        GitSync.sync(phoneA, credentials = null)
        val onA = phoneA.list()!!.filter { it.endsWith(".md") }.sorted()
        lines += "A after sync: $onA"
        lines += if (second.conflicts.size == 1 && onA.size == 2) "✓ JGit sync + conflict copy work on device" else "✗ unexpected result"
        return lines
    }

    /** Runs one step and reports its result and time, or its error, as one line. */
    private fun step(
        name: String,
        block: () -> String,
    ): String =
        try {
            var result = ""
            val millis = measureTimeMillis { result = block() }
            "✓ $name: $result, $millis ms"
        } catch (error: Throwable) {
            "✗ $name: ${error::class.simpleName} ${error.message?.take(80)}"
        }

    private fun freshDir(
        context: Context,
        name: String,
    ): File =
        File(context.filesDir, name).apply {
            deleteRecursively()
            mkdirs()
        }
}
