/**
 * A path inside a vault, relative to the vault root, with `/` between parts:
 * `""` is the root, `"Projects"` a folder, `"Projects/Plan.md"` a note.
 *
 * Everything in `:core` uses `VaultPath` instead of `java.io.File` or an Android `Uri`, so the
 * same code works for a folder picked with Android's file picker, a plain folder, and tests.
 */
package com.dotnote.core

data class VaultPath(
    val value: String,
) : Comparable<VaultPath> {
    init {
        require(!value.startsWith("/") && !value.endsWith("/")) { "VaultPath must not start or end with '/': $value" }
        require(!value.contains("//")) { "VaultPath must not contain empty parts: $value" }
    }

    /** True for the vault root itself. */
    val isRoot: Boolean get() = value.isEmpty()

    /** The parts between the slashes. Empty for the root. */
    val segments: List<String> get() = if (isRoot) emptyList() else value.split('/')

    /** The last part: `"Plan.md"` for `"Projects/Plan.md"`. Empty for the root. */
    val name: String get() = value.substringAfterLast('/')

    /** The folder this path is in, or `null` for the root. */
    val parent: VaultPath?
        get() =
            when {
                isRoot -> null
                '/' !in value -> ROOT
                else -> VaultPath(value.substringBeforeLast('/'))
            }

    /** The path of [childName] inside this folder. */
    fun child(childName: String): VaultPath {
        require(childName.isNotEmpty() && '/' !in childName) { "Not a single name: $childName" }
        return if (isRoot) VaultPath(childName) else VaultPath("$value/$childName")
    }

    /** True if this path is [other] or inside it (compares whole parts, so "ab" is not inside "a"). */
    fun isInside(other: VaultPath): Boolean = other.isRoot || value == other.value || value.startsWith(other.value + "/")

    override fun compareTo(other: VaultPath): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        val ROOT = VaultPath("")

        /** Builds a path from parts, skipping empty ones: `of("a", "b.md")` → `"a/b.md"`. */
        fun of(vararg parts: String): VaultPath = VaultPath(parts.filter { it.isNotEmpty() }.joinToString("/"))
    }
}
