/**
 * The errors `:core` throws. The message is shown to the user, so keep it short and plain.
 *
 * It's a "sealed" class: every kind of error is listed here, so a `when` over a `CoreError`
 * can check that it handles all of them.
 */
package com.kunotes.core

sealed class CoreError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class InvalidName(
        reason: String,
    ) : CoreError("Invalid name: $reason")

    class AlreadyExists(
        val path: VaultPath,
    ) : CoreError("\"${path.name}\" already exists")

    class NotFound(
        val path: VaultPath,
    ) : CoreError("\"${path.name}\" doesn't exist anymore")

    class MoveIntoSelf : CoreError("A folder can't be moved into itself or one of its subfolders")

    class NotAnImage(
        fileName: String,
    ) : CoreError("\"$fileName\" isn't an image (use PNG, JPEG, GIF, WebP, SVG or BMP)")

    /** Locking, unlocking, or the vault password; the message says why. */
    class Lock(
        message: String,
    ) : CoreError(message)

    /** A git sync step failed; the message says why. */
    class Git(
        message: String,
        cause: Throwable? = null,
    ) : CoreError(message, cause)

    /** Reading or writing failed (disk full, permission lost, ...). */
    class Io(
        message: String,
        cause: Throwable? = null,
    ) : CoreError(message, cause)
}
