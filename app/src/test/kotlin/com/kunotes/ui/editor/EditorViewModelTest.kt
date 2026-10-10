package com.kunotes.ui.editor

import androidx.compose.runtime.snapshots.Snapshot
import com.kunotes.core.VaultPath
import com.kunotes.settings.ViewMode
import com.kunotes.ui.dotline.FormatAction
import com.kunotes.vault.VaultEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import androidx.compose.ui.text.TextRange as ComposeRange

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val backend = FakeEditorBackend()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Opens [path] (with [content] on disk) and waits until it's loaded. */
    private fun TestScope.open(
        path: String = "Notes/Plan.md",
        content: String = "# Plan\nfirst line\n",
    ): EditorViewModel {
        backend.memoryFs.put(path, content)
        // backgroundScope stands in for the app scope: it lives for the whole test.
        val viewModel = EditorViewModel(VaultPath(path), backend, backgroundScope)
        runCurrent()
        return viewModel
    }

    /** Types [text] at the end, like the keyboard would, and lets Compose report the change. */
    private fun TestScope.type(
        viewModel: EditorViewModel,
        text: String,
    ) {
        viewModel.text.edit {
            append(text)
            selection = ComposeRange(length)
        }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun `loads the note with LF line endings`() =
        runTest {
            val viewModel = open(content = "# Plan\r\nfirst line\r\n")
            assertEquals("# Plan\nfirst line\n", viewModel.text.text.toString())
            assertTrue(viewModel.ui.value.loaded)
            assertEquals(com.kunotes.core.LineEnding.CRLF, viewModel.ui.value.lineEnding)
            assertEquals(emptyList<VaultPath>(), backend.writes, "opening a note never writes it")
        }

    @Test
    fun `saves 500 ms after the last edit, keeping CRLF`() =
        runTest {
            val viewModel = open(content = "a\r\nb\r\n")
            type(viewModel, "c")
            assertEquals(SaveState.Saving, viewModel.saveState.value)
            advanceTimeBy(300)
            type(viewModel, "d") // restarts the delay
            advanceTimeBy(499)
            runCurrent()
            assertEquals(emptyList<VaultPath>(), backend.writes)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf(VaultPath("Notes/Plan.md")), backend.writes, "one save for both edits")
            assertEquals("a\r\nb\r\ncd", backend.memoryFs.text("Notes/Plan.md"))
            assertEquals(SaveState.Saved, viewModel.saveState.value)
        }

    @Test
    fun `flush saves at once`() =
        runTest {
            val viewModel = open()
            type(viewModel, "more")
            viewModel.onStop()
            runCurrent()
            assertEquals("# Plan\nfirst line\nmore", backend.memoryFs.text("Notes/Plan.md"))
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(1, backend.writes.size, "the flushed save isn't repeated")
        }

    @Test
    fun `switching modes never writes and is remembered`() =
        runTest {
            val content = "# Plan\r\n- [ ] task\r\n**bold** and `code`\r\n"
            val viewModel = open(content = content)
            for (mode in listOf(ViewMode.Source, ViewMode.Preview, ViewMode.Live, ViewMode.Preview)) {
                viewModel.setMode(mode)
                runCurrent()
            }
            viewModel.onStop()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(emptyList<VaultPath>(), backend.writes)
            assertEquals(content, backend.memoryFs.text("Notes/Plan.md"))
            assertEquals(ViewMode.Preview, backend.viewMode)
            assertEquals(ViewMode.Preview, viewModel.ui.value.mode)
        }

    @Test
    fun `starts in the remembered mode`() =
        runTest {
            backend.viewMode = ViewMode.Source
            val viewModel = open()
            assertEquals(ViewMode.Source, viewModel.ui.value.mode)
        }

    @Test
    fun `follows the note when its folder is moved`() =
        runTest {
            val viewModel = open()
            type(viewModel, "x")
            backend.vaultEvents.emit(VaultEvent.Moved(VaultPath("Notes"), VaultPath("Archive/Notes")))
            runCurrent()
            assertEquals(VaultPath("Archive/Notes/Plan.md"), viewModel.ui.value.path)
            advanceTimeBy(600)
            runCurrent()
            assertEquals(listOf(VaultPath("Archive/Notes/Plan.md")), backend.writes, "the waiting save goes to the new place")
        }

    @Test
    fun `rename saves first, then renames`() =
        runTest {
            val viewModel = open()
            type(viewModel, "!")
            var done = false
            viewModel.rename("Goals") { done = true }
            runCurrent()
            assertTrue(done)
            assertEquals(VaultPath("Notes/Goals.md"), viewModel.ui.value.path)
            assertEquals("# Plan\nfirst line\n!", backend.memoryFs.text("Notes/Goals.md"))
            assertEquals(null, backend.memoryFs.text("Notes/Plan.md"))
        }

    @Test
    fun `a failed save keeps the text and tries again`() =
        runTest {
            val viewModel = open()
            backend.failWrites = IOException("Disk full")
            type(viewModel, "1")
            advanceTimeBy(600)
            runCurrent()
            assertEquals(SaveState.Failed("Disk full"), viewModel.saveState.value)
            backend.failWrites = null
            viewModel.flush()
            runCurrent()
            assertEquals("# Plan\nfirst line\n1", backend.memoryFs.text("Notes/Plan.md"))
            assertEquals(SaveState.Saved, viewModel.saveState.value)
        }

    @Test
    fun `a locked note asks for unlock and is never written`() =
        runTest {
            val viewModel = open(path = "Secret.md.age", content = "-----BEGIN AGE ENCRYPTED FILE-----")
            assertTrue(viewModel.ui.value.isLocked)
            viewModel.onStop()
            runCurrent()
            assertEquals(emptyList<VaultPath>(), backend.writes)
        }

    @Test
    fun `reloads a note changed on disk when there are no edits`() =
        runTest {
            val viewModel = open()
            backend.memoryFs.put("Notes/Plan.md", "changed elsewhere")
            viewModel.onStart()
            runCurrent()
            Snapshot.sendApplyNotifications()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals("changed elsewhere", viewModel.text.text.toString())
            assertEquals(emptyList<VaultPath>(), backend.writes, "reloading doesn't save")
        }

    @Test
    fun `keeps unsaved edits instead of reloading`() =
        runTest {
            val viewModel = open()
            type(viewModel, "mine")
            backend.memoryFs.put("Notes/Plan.md", "changed elsewhere")
            viewModel.onStart()
            runCurrent()
            assertEquals("# Plan\nfirst line\nmine", viewModel.text.text.toString())
        }

    @Test
    fun `formatter buttons edit the buffer and save`() =
        runTest {
            val viewModel = open(content = "word")
            viewModel.text.edit { selection = ComposeRange(0, 4) }
            viewModel.format(FormatAction.Bold)
            Snapshot.sendApplyNotifications()
            advanceTimeBy(600)
            runCurrent()
            assertEquals("**word**", backend.memoryFs.text("Notes/Plan.md"))
            assertEquals(ComposeRange(2, 6), viewModel.text.selection)
        }

    @Test
    fun `heading button cycles H1 to H3`() =
        runTest {
            val viewModel = open(content = "Title")
            val levels =
                (1..4).map {
                    viewModel.format(FormatAction.Heading)
                    viewModel.text.text
                        .toString()
                        .substringBefore(' ')
                }
            assertEquals(listOf("#", "##", "###", "#"), levels)
        }

    @Test
    fun `tapping a task box toggles it`() =
        runTest {
            val viewModel = open(content = "- [ ] one\n- [x] two")
            viewModel.toggleTask(0)
            viewModel.toggleTask(10)
            assertEquals("- [x] one\n- [ ] two", viewModel.text.text.toString())
        }

    @Test
    fun `trash saves first and stops saving`() =
        runTest {
            val viewModel = open()
            type(viewModel, "last words")
            viewModel.trash()
            runCurrent()
            assertEquals("# Plan\nfirst line\nlast words", backend.memoryFs.text("Notes/Plan.md"))
            assertEquals(listOf(VaultPath("Notes/Plan.md")), backend.trashed)
        }
}
