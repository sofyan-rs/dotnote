/**
 * The app's screens as type-safe routes for Navigation Compose (SPEC §4).
 *
 * Each `@Serializable` object or class is one destination; its fields are the arguments.
 * Navigate with `navController.navigate(Editor(path = "Projects/Plan.md"))`.
 * Sheets and dialogs are not routes: screens show them from their own state.
 */
package com.kunotes.ui.navigation

import kotlinx.serialization.Serializable

/** First launch (1.1), with the open-folder explainer sheet (1.2). */
@Serializable
data object Welcome

/** The four bottom-nav tabs. */
@Serializable
data object Files

@Serializable
data object Search

@Serializable
data object Sync

@Serializable
data object Vaults

/** A note in the editor (3.x); [path] is vault-relative. */
@Serializable
data class Editor(
    val path: String,
)

/** Full-screen folder picker for "Move to…" (2.5); [path] is the item being moved. */
@Serializable
data class MoveTo(
    val path: String,
)

/** Clone a git repository as a new vault (SPEC 7, clone flow). */
@Serializable
data object Clone
