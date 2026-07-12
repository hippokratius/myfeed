package de.hippokratius.kvaesitsorss.ui

import androidx.annotation.StringRes
import de.hippokratius.kvaesitsorss.R
import de.hippokratius.kvaesitsorss.core.catalog.CatalogCategory

/** Lokalisierter Anzeigename einer Katalog-Kategorie. */
@StringRes
fun CatalogCategory.labelRes(): Int = when (this) {
    CatalogCategory.NEWS -> R.string.category_news
    CatalogCategory.REGIONAL -> R.string.category_regional
    CatalogCategory.TECH -> R.string.category_tech
    CatalogCategory.SPORT -> R.string.category_sport
    CatalogCategory.SCIENCE -> R.string.category_science
    CatalogCategory.ECONOMY -> R.string.category_economy
    CatalogCategory.CULTURE -> R.string.category_culture
}
