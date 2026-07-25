package de.hippokratius.myfeed.core

/**
 * Markiert Code der Nextcloud-News-Anbindung als Beta: Funktionsumfang und
 * Verhalten können sich noch ändern (docs/konzept-nextcloud-news.md §10,
 * Phase 3 offen). Rein dokumentierend – kein Opt-in-Zwang.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
)
annotation class Beta
