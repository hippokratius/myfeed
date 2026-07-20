package de.hippokratius.myfeed.ui

import androidx.annotation.StringRes
import de.hippokratius.myfeed.R
import de.hippokratius.myfeed.core.catalog.CatalogCategory

/** Lokalisierter Anzeigename einer Katalog-Kategorie. */
@StringRes
fun CatalogCategory.labelRes(): Int = when (this) {
    CatalogCategory.NEWS -> R.string.category_news
    CatalogCategory.POLITICS -> R.string.category_politics
    CatalogCategory.REGIONAL -> R.string.category_regional
    CatalogCategory.TECH -> R.string.category_tech
    CatalogCategory.GAMING -> R.string.category_gaming
    CatalogCategory.SPORT -> R.string.category_sport
    CatalogCategory.SCIENCE -> R.string.category_science
    CatalogCategory.ECONOMY -> R.string.category_economy
    CatalogCategory.CULTURE -> R.string.category_culture
    CatalogCategory.SATIRE -> R.string.category_satire
}
