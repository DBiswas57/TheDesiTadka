package com.thedesitadka.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ContentCategoryDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String? = null,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val providerIds: List<String> = emptyList()
) {
    companion object {
        val DEFAULT_CATEGORIES = listOf(
            ContentCategoryDefinition(
                id = "all",
                name = "All",
                description = "All available aggregated content",
                sortOrder = 0,
                enabled = true
            ),
            ContentCategoryDefinition(
                id = "desi",
                name = "Desi Panu",
                description = "Indian and desi adult content",
                sortOrder = 1,
                enabled = true,
                providerIds = listOf(
                    "kamababa1", "masa49", "fsiblogxx", "masahub2", "aagmaal",
                    "fry99", "hitmaal", "webxseries", "desibf", "antarvasnabf",
                    "desisex", "ixiporn", "masafun", "uncutmaza", "wowuncut",
                    "desikahani2", "desitales2", "indiansexstories3", "xxxindianstories", "xmaza",
                    "aagmaal_com", "bmaal", "chiggywiggy", "desibabe", "desigirlxx",
                    "desimaals", "desivideo", "lalamasa", "watchoerotic"
                )
            ),
            ContentCategoryDefinition(
                id = "english",
                name = "English Porn",
                description = "International and western adult content",
                sortOrder = 2,
                enabled = true,
                providerIds = listOf(
                    "brazzpw", "fpo", "hello", "hqporner", "max",
                    "netfapx", "ok_porn", "ok_xxx", "perfectgirls", "porn4days",
                    "pornhat", "pornhd4k", "pornhouse", "pornmz", "pornstars_tube",
                    "sxyprn", "watchxxxfree", "definebabe"
                )
            ),
            ContentCategoryDefinition(
                id = "tube",
                name = "Tube Type",
                description = "Major global tube video aggregators",
                sortOrder = 3,
                enabled = true,
                providerIds = listOf(
                    "xnxx", "xvideos", "pornhub", "xhamster", "redtube",
                    "youporn", "tube8", "freeonestube", "spankbang", "tnaflix",
                    "empflix", "beeg", "youjizz", "eporner", "drtuber",
                    "nuvid", "pornone", "three_movs", "txxx", "upornia", "hdzog"
                )
            ),
            ContentCategoryDefinition(
                id = "movies",
                name = "Movies & Series",
                description = "Bollywood, Hollywood, Cinema & OTT Web Series",
                sortOrder = 4,
                enabled = true,
                providerIds = listOf(
                    "movienerds", "cineapse", "prmovies", "prmovies_church"
                )
            ),
            ContentCategoryDefinition(
                id = "premium",
                name = "Premium Content",
                description = "Authorized preview and premium catalog listings",
                sortOrder = 5,
                enabled = true,
                providerIds = listOf(
                    "fry99", "webxseries", "uncutmaza", "wowuncut", "brazzpw",
                    "hqporner", "pornhd4k", "netfapx", "perfectgirls", "pornhouse",
                    "watchxxxfree", "watchoerotic"
                )
            ),
            ContentCategoryDefinition(
                id = "downloadable",
                name = "Downloadable",
                description = "Content available for offline download",
                sortOrder = 6,
                enabled = true
            )
        )
    }
}
