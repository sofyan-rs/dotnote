package com.kunotes.core

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Git sync against a real (local, bare) remote, standing in for GitHub; two vault folders stand in
 * for two devices (desktop `tests/git.rs`, plus the cases JGit handles differently from the git CLI).
 */
class GitSyncTest {
    @TempDir
    lateinit var temp: File

    /** A bare repository standing in for GitHub, and its address. */
    private fun remote(name: String = "remote.git"): String {
        val dir = File(temp, name)
        Git
            .init()
            .setBare(true)
            .setInitialBranch("main")
            .setDirectory(dir)
            .call()
            .close()
        return dir.path
    }

    private fun vault(name: String): File = File(temp, name).apply { mkdirs() }

    private fun File.write(
        name: String,
        text: String,
    ) = File(this, name).apply { parentFile.mkdirs() }.writeText(text)

    private fun File.read(name: String): String = File(this, name).readText()

    private fun File.has(name: String): Boolean = File(this, name).exists()

    @Test
    fun `notes travel between two devices`() {
        val url = remote()
        val laptop = vault("laptop")
        laptop.write("Hello.md", "# Hello\n")
        GitSync.connect(laptop, url, null)
        assertEquals(url, GitSync.remoteUrl(laptop))
        assertEquals(GitSync.GITIGNORE, laptop.read(".gitignore"))

        // A second device with its own note joins and gets both.
        val phone = vault("phone")
        phone.write("Other.md", "other\n")
        val joined = GitSync.connect(phone, url, null)
        assertTrue(phone.has("Hello.md"))
        assertTrue(VaultPath("Hello.md") in joined.changed)

        // An edit on the phone reaches the laptop.
        phone.write("Hello.md", "# Hello\nfrom phone\n")
        GitSync.sync(phone, null)
        val report = GitSync.sync(laptop, null)
        assertEquals("# Hello\nfrom phone\n", laptop.read("Hello.md"))
        assertTrue(laptop.has("Other.md"))
        assertTrue(VaultPath("Hello.md") in report.changed)
        assertEquals(emptyList<VaultPath>(), report.conflicts)

        // Nothing new: nothing changed.
        assertEquals(SyncReport(branch = "main"), GitSync.sync(laptop, null))
    }

    @Test
    fun `a file changed on both sides keeps both versions`() {
        val url = remote()
        val a = vault("a")
        a.write("Plan.md", "start\n")
        GitSync.connect(a, url, null)
        val b = vault("b")
        GitSync.connect(b, url, null)

        a.write("Plan.md", "from a\n")
        GitSync.sync(a, null)
        b.write("Plan.md", "from b\n")
        val report = GitSync.sync(b, null)

        assertEquals("from b\n", b.read("Plan.md"), "ours stays")
        assertEquals(1, report.conflicts.size)
        val copy = report.conflicts.single()
        assertTrue(copy.name.startsWith("Plan (conflict "), copy.name)
        assertEquals("from a\n", b.read(copy.value), "theirs is kept as a copy")

        // The resolution reaches the other side too.
        GitSync.sync(a, null)
        assertTrue(a.has(copy.value))
        assertEquals("from b\n", a.read("Plan.md"))
    }

    @Test
    fun `a locked note in conflict keeps both extensions`() {
        val url = remote()
        val a = vault("a")
        a.write("Keys.md.age", "-----BEGIN AGE ENCRYPTED FILE-----\none\n")
        GitSync.connect(a, url, null)
        val b = vault("b")
        GitSync.connect(b, url, null)

        a.write("Keys.md.age", "-----BEGIN AGE ENCRYPTED FILE-----\ntwo\n")
        GitSync.sync(a, null)
        b.write("Keys.md.age", "-----BEGIN AGE ENCRYPTED FILE-----\nthree\n")
        val copy = GitSync.sync(b, null).conflicts.single()
        assertEquals(VaultPath("Keys (conflict ${GitConflicts.today()}).md.age"), copy)
        assertTrue(b.read(copy.value).endsWith("two\n"))
        assertTrue(b.read("Keys.md.age").endsWith("three\n"))
    }

    @Test
    fun `a delete on one side and an edit on the other keeps the edit`() {
        val url = remote()
        val a = vault("a")
        a.write("Gone here.md", "1\n")
        a.write("Gone there.md", "1\n")
        GitSync.connect(a, url, null)
        val b = vault("b")
        GitSync.connect(b, url, null)

        File(a, "Gone here.md").delete()
        a.write("Gone there.md", "edited on a\n")
        GitSync.sync(a, null)
        b.write("Gone here.md", "edited on b\n")
        File(b, "Gone there.md").delete()
        val report = GitSync.sync(b, null)

        assertEquals("edited on b\n", b.read("Gone here.md"), "changed here, deleted there: ours stays")
        assertEquals("edited on a\n", b.read("Gone there.md"), "deleted here, changed there: theirs comes back")
        assertEquals(emptyList<VaultPath>(), report.conflicts)
    }

    @Test
    fun `a vault with its own history merges with the remote (unrelated histories)`() {
        val url = remote()
        val a = vault("a")
        a.write("From a.md", "a\n")
        GitSync.connect(a, url, null)

        // Device b already has its own repository and commits, never related to the remote.
        val b = vault("b")
        b.write("From b.md", "b\n")
        Git.init().setDirectory(b).setInitialBranch("main").call().use { git ->
            git.add().addFilepattern(".").call()
            val me = PersonIdent("Someone", "someone@example.com")
            git
                .commit()
                .setMessage("old history")
                .setAuthor(me)
                .setCommitter(me)
                .setSign(false)
                .call()
        }
        GitSync.connect(b, url, null)
        assertTrue(b.has("From a.md"))
        assertTrue(b.has("From b.md"))

        GitSync.sync(a, null)
        assertTrue(a.has("From b.md"))
    }

    @Test
    fun `an empty remote gets this vault's notes on main`() {
        val url = remote()
        val a = vault("a")
        a.write("First.md", "first\n")
        val report = GitSync.connect(a, url, null)
        assertEquals(listOf(".gitignore", "First.md"), report.pushed.map { it.value }.sorted())
        assertEquals(emptyList<VaultPath>(), report.changed)
        Git.open(File(url)).use { bare -> assertNotNull(bare.repository.resolve("refs/heads/main")) }

        val b = vault("b")
        GitSync.connect(b, "file://$url", null)
        assertEquals("first\n", b.read("First.md"))
    }

    @Test
    fun `the trash stays on this device and line endings are kept`() {
        val url = remote()
        val a = vault("a")
        a.write(".trash/Old.md", "old\n")
        a.write("Windows.md", "line one\r\nline two\r\n")
        GitSync.connect(a, url, null)

        val b = vault("b")
        GitSync.connect(b, url, null)
        assertFalse(b.has(".trash/Old.md"), ".trash/ is never synced")
        assertEquals("line one\r\nline two\r\n", b.read("Windows.md"), "byte for byte")
        assertEquals(GitSync.GITIGNORE, b.read(".gitignore"), "same .gitignore as desktop, so no conflict")
    }

    @Test
    fun `a sync reports the branch and the files it pushed`() {
        val url = remote()
        val a = vault("a")
        GitSync.connect(a, url, null)
        a.write("One.md", "1\n")
        a.write("Two.md", "2\n")
        val report = GitSync.sync(a, null)
        assertEquals("main", report.branch)
        assertEquals(listOf("One.md", "Two.md"), report.pushed.map { it.value }.sorted())
        assertEquals(emptyList<VaultPath>(), report.changed)
    }

    @Test
    fun `cloning keeps the remote's own gitignore without conflict copies`() {
        val url = remote()
        val desktop = vault("desktop")
        desktop.write(".gitignore", "*.log\n")
        desktop.write("Note.md", "hi\n")
        Git.init().setDirectory(desktop).setInitialBranch("main").call().use { git ->
            git.add().addFilepattern(".").call()
            val me = PersonIdent("Me", "me@example.com")
            git.commit().setMessage("first").setAuthor(me).setCommitter(me).call()
            git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(url)).call()
            git.push().setRemote("origin").setRefSpecs(org.eclipse.jgit.transport.RefSpec("HEAD:refs/heads/main")).call()
        }

        val phone = File(temp, "phone")
        GitSync.clone(phone, url, null)
        assertEquals("hi\n", phone.read("Note.md"))
        assertEquals("*.log\n", phone.read(".gitignore"))
        assertEquals(listOf(".git", ".gitignore", "Note.md"), phone.list()?.sorted())
        assertEquals(url, GitSync.remoteUrl(phone))

        phone.write("Note.md", "edited\n")
        assertEquals(listOf("Note.md"), GitSync.sync(phone, null).pushed.map { it.value })
    }

    @Test
    fun `cloning an empty remote works and refuses a folder that isn't empty`() {
        val url = remote()
        val phone = File(temp, "phone")
        GitSync.clone(phone, url, null)
        assertEquals(GitSync.GITIGNORE, phone.read(".gitignore"))

        val busy = vault("busy")
        busy.write("Old.md", "old\n")
        assertThrows<CoreError.Git> { GitSync.clone(busy, url, null) }
        val missing = File(temp, "nope.git").path
        val error = assertThrows<CoreError.Git> { GitSync.clone(File(temp, "c"), missing, null) }
        assertTrue(error.message.orEmpty().contains("wasn't found"), error.message)
    }

    @Test
    fun `a bad address gives a readable error`() {
        val v = vault("v")
        val missing = File(temp, "nope.git").path
        val error = assertThrows<CoreError.Git> { GitSync.connect(v, missing, null) }
        assertTrue(error.message.orEmpty().contains("wasn't found"), error.message)
    }

    @Test
    fun `an empty address is refused`() {
        val error = assertThrows<CoreError.Git> { GitSync.connect(vault("v"), "  ", null) }
        assertEquals("Enter the address of a git repository.", error.message)
    }

    @Test
    fun `disconnecting removes the remote`() {
        val v = vault("v")
        assertNull(GitSync.remoteUrl(v), "not a repository yet")
        GitSync.connect(v, remote(), null)
        GitSync.disconnect(v)
        assertNull(GitSync.remoteUrl(v))
        assertTrue(v.has(".git"), "the history stays")
    }

    @Test
    fun `quitting commits and pushes`() {
        val url = remote()
        val a = vault("a")
        GitSync.connect(a, url, null)
        a.write("Late.md", "typed just before quitting\n")
        assertTrue(GitSync.commitAndPushQuickly(a, null, timeoutSeconds = 10))

        val b = vault("b")
        GitSync.connect(b, url, null)
        assertTrue(b.has("Late.md"))
    }

    @Test
    fun `sign-in errors are explained and tokens are never printed`() {
        val auth =
            org.eclipse.jgit.api.errors
                .TransportException("https://github.com/me/notes.git: not authorized")
        assertTrue(GitSync.friendlyError(auth).contains("sign in"), GitSync.friendlyError(auth))
        assertEquals("something odd", GitSync.friendlyError(IllegalStateException("something odd\n")))
        assertFalse(GitCredentials("me", "ghp_secret").toString().contains("ghp_secret"))
    }
}
