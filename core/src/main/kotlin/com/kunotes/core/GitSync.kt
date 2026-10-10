/**
 * Git sync: keeps a vault in step with a git remote (e.g. a GitHub repo), using JGit.
 * Port of desktop's `git.rs`. Desktop runs the `git` program; Android has none, so this uses JGit,
 * which needs a real folder (`java.io.File`), not a [VaultFs].
 */
package com.kunotes.core

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult.MergeStatus
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import java.io.File
import java.io.IOException

/** What a sync did. */
data class SyncReport(
    /** Files the merge changed on this device (to reload open notes). */
    val changed: List<VaultPath> = emptyList(),
    /** Copies saved because a file changed on both sides. */
    val conflicts: List<VaultPath> = emptyList(),
)

/** HTTPS sign-in for the remote: a user name and a personal access token. */
data class GitCredentials(
    val username: String,
    val token: String,
) {
    // A data class prints all its fields by default; never let the token end up in a log.
    override fun toString(): String = "GitCredentials(username=$username, token=***)"
}

/**
 * One sync is: commit local changes, fetch, merge the remote, push. When the same file changed on
 * both sides, nothing is lost: this device's version stays and the other one is saved next to it
 * (see [GitConflicts]).
 *
 * Every function blocks while git works (disk and network), so call them on `Dispatchers.IO`.
 * Failures throw [CoreError.Git] with a message for the user.
 * Pass `credentials = null` for remotes that need no sign-in (a local folder, in tests).
 */
object GitSync {
    /** Written to `.gitignore` when connecting a vault that has none. Same bytes as desktop. */
    internal const val GITIGNORE = "# Written by KuNotes\n.DS_Store\nThumbs.db\n.*.kunotes.tmp\n"

    /** Folders this device never syncs: the app's own trash (desktop uses the system trash). */
    private const val LOCAL_EXCLUDES = "# Written by KuNotes\n.trash/\n"

    private const val REMOTE = "origin"
    private const val UPDATE_MESSAGE = "Update notes (KuNotes)"
    private const val MERGE_MESSAGE = "Merge notes from the remote (KuNotes)"

    /** The remote the vault syncs with, if it has one. */
    fun remoteUrl(root: File): String? {
        if (!File(root, ".git").exists()) return null
        return runGit { open(root).use { git -> remoteUrl(git) } }
    }

    /**
     * Starts syncing [root] with [url]: makes the vault a git repository if it isn't one, sets
     * the remote, and runs a first sync (which brings in the remote's notes and merges them with
     * the ones already here).
     */
    fun connect(
        root: File,
        url: String,
        credentials: GitCredentials?,
    ): SyncReport {
        val address = url.trim()
        if (address.isEmpty()) throw CoreError.Git("Enter the address of a git repository.")
        runGit {
            if (!File(root, ".git").exists()) {
                // A new repository starts on `main`, whatever the default is.
                Git
                    .init()
                    .setDirectory(root)
                    .setInitialBranch("main")
                    .call()
                    .close()
            }
            val fs = FileVaultFs(root)
            if (!fs.exists(VaultPath(".gitignore"))) {
                fs.writeAtomic(VaultPath(".gitignore"), GITIGNORE.toByteArray())
            }
            // `.trash/` goes in `.git/info/exclude`, not `.gitignore`: it only exists on Android, and
            // a `.gitignore` that differs from desktop's would conflict on the first sync.
            val exclude = File(root, ".git/info/exclude")
            if (!exclude.exists() || ".trash/" !in exclude.readText()) {
                exclude.parentFile.mkdirs()
                val old = if (exclude.exists()) exclude.readText() else ""
                val separator = if (old.isEmpty() || old.endsWith("\n")) "" else "\n"
                exclude.appendText(separator + LOCAL_EXCLUDES)
            }
            open(root).use { git ->
                if (remoteUrl(git) == null) {
                    git
                        .remoteAdd()
                        .setName(REMOTE)
                        .setUri(URIish(address))
                        .call()
                } else {
                    git
                        .remoteSetUrl()
                        .setRemoteName(REMOTE)
                        .setRemoteUri(URIish(address))
                        .call()
                }
            }
        }
        return sync(root, credentials)
    }

    /** Stops syncing: removes the remote. The history in `.git` stays. */
    fun disconnect(root: File) {
        runGit { open(root).use { git -> git.remoteRemove().setRemoteName(REMOTE).call() } }
    }

    /** One sync: commit, fetch, merge, push. */
    fun sync(
        root: File,
        credentials: GitCredentials?,
    ): SyncReport =
        runGit {
            open(root).use { git -> syncOpen(git, FileVaultFs(root), provider(credentials)) }
        }

    /**
     * Commits local changes and tries to push them, giving up after [timeoutSeconds] (for the
     * background job when the app goes away, so a slow network can't keep it running).
     * Returns `true` if the push went through. A failed push is fine: the commit is safe locally
     * and goes out with the next sync.
     */
    fun commitAndPushQuickly(
        root: File,
        credentials: GitCredentials?,
        timeoutSeconds: Int,
    ): Boolean =
        runGit {
            open(root).use { git ->
                commitChanges(git)
                val branch = remoteBranch(git, provider(credentials), timeoutSeconds) ?: return@use false
                try {
                    push(git, provider(credentials), branch, timeoutSeconds)
                    true
                } catch (_: CoreError.Git) {
                    false
                } catch (_: GitAPIException) {
                    false
                }
            }
        }

    // ----- Steps -----

    private fun syncOpen(
        git: Git,
        fs: VaultFs,
        credentials: CredentialsProvider?,
    ): SyncReport {
        commitChanges(git)
        val branch = remoteBranch(git, credentials)
        if (branch == null) {
            // An empty remote: just send what's here.
            if (head(git) != null) push(git, credentials, "main")
            return SyncReport()
        }

        val theirsRef = "refs/remotes/$REMOTE/$branch"
        git
            .fetch()
            .setRemote(REMOTE)
            .setRefSpecs(RefSpec("+refs/heads/$branch:$theirsRef"))
            .setCredentialsProvider(credentials)
            .call()
        val theirs = git.repository.resolve(theirsRef) ?: throw CoreError.Git("The remote branch \"$branch\" is missing.")
        val before = head(git)

        var report = SyncReport()
        val upToDate = before != null && isAncestor(git, theirs, before)
        if (!upToDate) {
            val result =
                git
                    .merge()
                    .include(theirs)
                    // Always merge (unrelated histories too) and make the commit ourselves, so our
                    // identity and "no signing" settings apply, like desktop's `commit_settings`.
                    .setFastForward(MergeCommand.FastForwardMode.FF)
                    .setCommit(false)
                    .call()
            val conflicts =
                when (result.mergeStatus) {
                    MergeStatus.FAST_FORWARD, MergeStatus.ALREADY_UP_TO_DATE -> emptyList()
                    MergeStatus.MERGED_NOT_COMMITTED -> {
                        commit(git, MERGE_MESSAGE)
                        emptyList()
                    }
                    MergeStatus.CONFLICTING -> {
                        val copies = GitConflicts.resolve(git, fs)
                        commit(git, MERGE_MESSAGE)
                        copies
                    }
                    else -> throw CoreError.Git("Couldn't merge the remote's notes (${result.mergeStatus}).")
                }
            report = SyncReport(changed = changedFiles(git, before), conflicts = conflicts)
        }
        // Send our commits, if the remote doesn't have them yet.
        val now = head(git)
        if (now != null && !isAncestor(git, now, theirs)) push(git, credentials, branch)
        return report
    }

    /** Commits everything that changed, if anything did (`git add -A` + `git commit`). */
    private fun commitChanges(git: Git) {
        // Like `git add -A`, in two steps (JGit 5): new and changed files, then deleted files.
        git.add().addFilepattern(".").call()
        git
            .add()
            .addFilepattern(".")
            .setUpdate(true)
            .call()
        if (!git.status().call().hasUncommittedChanges()) return
        commit(git, UPDATE_MESSAGE)
    }

    private fun commit(
        git: Git,
        message: String,
    ) {
        val command =
            git
                .commit()
                .setMessage(message)
                .setNoVerify(true) // never run hooks
                .setSign(false) // never ask for a signing key
        // Use a stand-in name and email if git has none set (on a phone it never has).
        val email = git.repository.config.getString("user", null, "email")
        if (email.isNullOrBlank()) {
            val identity = PersonIdent("KuNotes", "kunotes@localhost")
            command.setAuthor(identity).setCommitter(identity)
        }
        command.call()
    }

    private fun push(
        git: Git,
        credentials: CredentialsProvider?,
        branch: String,
        timeoutSeconds: Int = 0,
    ) {
        val results =
            git
                .push()
                .setRemote(REMOTE)
                .setRefSpecs(RefSpec("HEAD:refs/heads/$branch"))
                .setCredentialsProvider(credentials)
                .setTimeout(timeoutSeconds) // 0 = no limit
                .call()
        // JGit doesn't throw when the remote refuses a push; the refusal is in the result.
        for (update in results.flatMap { it.remoteUpdates }) {
            when (update.status) {
                RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE -> Unit
                RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ->
                    throw CoreError.Git("The remote changed during the sync. Sync again.")
                else -> throw CoreError.Git("The remote refused the changes: ${update.message ?: update.status}")
            }
        }
    }

    /** The remote's default branch, or `null` if the remote is empty. */
    private fun remoteBranch(
        git: Git,
        credentials: CredentialsProvider?,
        timeoutSeconds: Int = 0,
    ): String? {
        val refs =
            git
                .lsRemote()
                .setRemote(REMOTE)
                .setCredentialsProvider(credentials)
                .setTimeout(timeoutSeconds) // 0 = no limit
                .callAsMap()
        val headTarget = refs[Constants.HEAD]?.takeIf { it.isSymbolic }?.target?.name
        if (headTarget != null && headTarget.startsWith(Constants.R_HEADS)) {
            return headTarget.removePrefix(Constants.R_HEADS)
        }
        // Some servers don't report HEAD; fall back to a branch that exists.
        val names =
            refs.keys
                .filter { it.startsWith(Constants.R_HEADS) }
                .map { it.removePrefix(Constants.R_HEADS) }
                .sorted()
        return listOf("main", "master").firstOrNull { it in names } ?: names.firstOrNull()
    }

    private fun head(git: Git): ObjectId? = git.repository.resolve(Constants.HEAD)

    /** True if commit [ancestor] is already part of [descendant]'s history. */
    private fun isAncestor(
        git: Git,
        ancestor: ObjectId,
        descendant: ObjectId,
    ): Boolean =
        RevWalk(git.repository).use { walk ->
            walk.isMergedInto(walk.parseCommit(ancestor), walk.parseCommit(descendant))
        }

    /** Files that differ between [before] and HEAD (all files if there was no [before]). */
    private fun changedFiles(
        git: Git,
        before: ObjectId?,
    ): List<VaultPath> {
        val repository = git.repository
        val after = head(git) ?: return emptyList()
        val changed = mutableListOf<VaultPath>()
        RevWalk(repository).use { revWalk ->
            TreeWalk(repository).use { walk ->
                walk.isRecursive = true
                if (before == null) {
                    walk.addTree(EmptyTreeIterator())
                } else {
                    walk.addTree(revWalk.parseCommit(before).tree)
                }
                walk.addTree(revWalk.parseCommit(after).tree)
                walk.filter = TreeFilter.ANY_DIFF
                while (walk.next()) changed += VaultPath(walk.pathString)
            }
        }
        return changed
    }

    // ----- Helpers -----

    private fun remoteUrl(git: Git): String? =
        git.repository.config
            .getString("remote", REMOTE, "url")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Opens the repository, set to sync files byte for byte (see below). */
    private fun open(root: File): Git {
        val git = Git.open(root)
        // No `\n` ⇄ `\r\n` rewriting: KuNotes keeps each file's own line endings (desktop passes
        // `-c core.autocrlf=false` to every git command; we store it in the repository instead).
        val config = git.repository.config
        if (config.getString("core", null, "autocrlf") != "false") {
            config.setString("core", null, "autocrlf", "false")
            config.save()
        }
        return git
    }

    private fun provider(credentials: GitCredentials?): CredentialsProvider? =
        credentials?.let { UsernamePasswordCredentialsProvider(it.username, it.token) }

    /** Runs [block] and turns JGit's errors into a [CoreError.Git] the user can act on. */
    private inline fun <T> runGit(block: () -> T): T =
        try {
            block()
        } catch (error: GitAPIException) {
            throw CoreError.Git(friendlyError(error), error)
        } catch (error: JGitInternalException) {
            throw CoreError.Git(friendlyError(error), error)
        } catch (error: IOException) {
            throw CoreError.Git(friendlyError(error), error)
        }

    /** Turns a JGit error (and its causes) into something a person can act on. */
    internal fun friendlyError(error: Throwable): String {
        val messages = generateSequence(error) { it.cause }.mapNotNull { it.message }.toList()
        val all = (messages + generateSequence(error) { it.cause }.map { it.javaClass.simpleName }).joinToString("\n")
        val lower = all.lowercase()
        return when {
            listOf("not authorized", "authentication is required", "auth fail", "authentication failed")
                .any { it in lower } ->
                "Git couldn't sign in to the remote. Check the user name and access token, then sync again."
            // JGit says "<address>: not found." with a NoRemoteRepositoryException.
            listOf("noremoterepositoryexception", "repository not found", "not a git repository")
                .any { it in lower } ->
                "The remote repository wasn't found. Check the address."
            listOf("unknownhost", "could not resolve host", "connection refused", "timed out", "connectexception")
                .any { it in lower } ->
                "Couldn't reach the remote. Check your internet connection."
            else ->
                messages
                    .firstOrNull { it.isNotBlank() }
                    ?.lines()
                    ?.last { it.isNotBlank() }
                    ?.trim() ?: "Git failed."
        }
    }
}
