package com.scouty.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRepositoriesTest {
    @Test
    fun normalizeLocalCode_uppercasesAndTrimsCompoundCodes() {
        val normalized = RouteEnrichmentRepository.normalizeLocalCode(" 05mn01 ; 05mn25 ")
        assertEquals("05MN01;05MN25", normalized)
    }

    @Test
    fun search_matchesAccentInsensitiveTextAndPrioritizesExactCode() {
        val catalog = RouteEnrichmentCatalog(
            routesByLocalCode = mapOf(
                "01MN02" to RouteEnrichmentEntry(
                    localCode = "01MN02",
                    displayTitle = "Vârful Caraiman",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Traseu spre Crucea Eroilor."),
                    image = RouteImage(imageUrl = "https://example.com/caraiman.jpg")
                ),
                "05MN11" to RouteEnrichmentEntry(
                    localCode = "05MN11",
                    displayTitle = "Cabana Omu",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Acces spre cabana de pe platou."),
                    image = RouteImage(imageUrl = "https://example.com/omu.jpg")
                )
            )
        )

        val textResults = RouteEnrichmentRepository.search(catalog, "varful caraiman")
        assertTrue(textResults.isNotEmpty())
        assertEquals("01MN02", textResults.first().localCode)

        val codeResults = RouteEnrichmentRepository.search(catalog, "05mn11")
        assertTrue(codeResults.isNotEmpty())
        assertEquals("05MN11", codeResults.first().localCode)
    }

    @Test
    fun search_prefersTitledMatches_filtersMissingImages_andDeduplicatesTitles() {
        val catalog = RouteEnrichmentCatalog(
            routesByLocalCode = mapOf(
                "01MN10" to RouteEnrichmentEntry(
                    localCode = "01MN10",
                    displayTitle = "Bușteni - Poiana Coștilei - Cabana Omu",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Traseu clasic spre Cabana Omu."),
                    image = RouteImage(thumbnailUrl = "https://example.com/omn10-thumb.jpg")
                ),
                "01MN10A" to RouteEnrichmentEntry(
                    localCode = "01MN10A",
                    displayTitle = "Bușteni - Poiana Coștilei - Cabana Omu",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Duplicat de test."),
                    image = RouteImage(imageUrl = "https://example.com/omn10.jpg")
                ),
                "01MN54" to RouteEnrichmentEntry(
                    localCode = "01MN54",
                    displayTitle = "Cabana Omu - Muntele Strungile - Cabana Padina",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Coborare dinspre Omu spre Padina."),
                    image = RouteImage(imageUrl = "https://example.com/omn54.jpg")
                ),
                "01MN07" to RouteEnrichmentEntry(
                    localCode = "01MN07",
                    displayTitle = "Bușteni - Cabana Piatra Arsă",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Descriere care mentioneaza Cabana Omu."),
                    image = RouteImage(imageUrl = "https://example.com/omn07.jpg")
                ),
                "01MN99" to RouteEnrichmentEntry(
                    localCode = "01MN99",
                    displayTitle = "Cabana Omu fără imagine",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Nu ar trebui sa apara in sugestii.")
                )
            )
        )

        val results = RouteEnrichmentRepository.search(catalog, "Cabana Omu", limit = 10)

        assertEquals(listOf("01MN54", "01MN10", "01MN07"), results.map { it.localCode })
    }

    @Test
    fun search_spreadsDuplicateImages_whenUniqueAlternativesExist() {
        val catalog = RouteEnrichmentCatalog(
            routesByLocalCode = mapOf(
                "01MN05" to RouteEnrichmentEntry(
                    localCode = "01MN05",
                    displayTitle = "Bușteni - Cascada Urlătoarea",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Traseu spre cascadă."),
                    image = RouteImage(
                        imageUrl = "https://example.com/urlatoarea-a.jpg",
                        sourcePageUrl = "https://commons.wikimedia.org/wiki/File:UrlatoareaA.jpg",
                        scope = "route_landmark"
                    )
                ),
                "01MN04" to RouteEnrichmentEntry(
                    localCode = "01MN04",
                    displayTitle = "Poiana Țapului - Cascada Urlătoarea",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Alt acces spre cascadă."),
                    image = RouteImage(
                        imageUrl = "https://example.com/urlatoarea-b.jpg",
                        sourcePageUrl = "https://commons.wikimedia.org/wiki/File:UrlatoareaA.jpg",
                        scope = "route_landmark"
                    )
                ),
                "01MN55" to RouteEnrichmentEntry(
                    localCode = "01MN55",
                    displayTitle = "Traseu Urlătoarea Clincii",
                    region = "Bucegi",
                    description = RouteDescription(textRo = "Traseu scurt în apropiere."),
                    image = RouteImage(
                        imageUrl = "https://example.com/urlatoarea-clincii.jpg",
                        sourcePageUrl = "https://commons.wikimedia.org/wiki/File:UrlatoareaClincii.jpg",
                        scope = "region_fallback"
                    )
                )
            )
        )

        val results = RouteEnrichmentRepository.search(catalog, "Urlătoarea", limit = 3)

        assertEquals(listOf("01MN05", "01MN55", "01MN04"), results.map { it.localCode })
    }

    @Test
    fun decodeSegments_decodesPolylineCoordinates() {
        val entry = RouteGeometryEntry(
            localCode = "01MN99",
            center = RouteCenter(lat = 0.0, lon = 0.0),
            bbox = RouteBounds(
                minLat = 38.5,
                minLon = -126.453,
                maxLat = 43.252,
                maxLon = -120.2
            ),
            segments = listOf("_p~iF~ps|U_ulLnnqC_mqNvxq`@"),
            pointCount = 3
        )

        val decoded = RouteGeometryRepository.decodeSegments(entry)
        assertEquals(1, decoded.size)
        assertEquals(3, decoded.first().size)
        assertEquals(38.5, decoded.first()[0].lat, 0.00001)
        assertEquals(-126.453, decoded.first()[2].lon, 0.00001)
    }

    @Test
    fun decodeSegments_splitsLargeCoordinateGapsIntoSeparateSegments() {
        val entry = RouteGeometryEntry(
            localCode = "01MN98",
            center = RouteCenter(lat = 45.0, lon = 25.0),
            bbox = RouteBounds(
                minLat = 45.0,
                minLon = 25.0,
                maxLat = 45.051,
                maxLon = 25.051
            ),
            segments = listOf("_atqG_yqwCgEgEgqHgqHgEgE"),
            pointCount = 4
        )

        val decoded = RouteGeometryRepository.decodeRenderableSegments(entry)

        assertEquals(2, decoded.size)
        assertEquals(2, decoded[0].size)
        assertEquals(2, decoded[1].size)
        assertEquals(45.001, decoded[0][1].lat, 0.00001)
        assertEquals(25.05, decoded[1][0].lon, 0.00001)
    }

    @Test
    fun decodeSegments_prunesShortTerminalSpurFromRenderableGeometry() {
        val entry = RouteGeometryEntry(
            localCode = "01MN97",
            center = RouteCenter(lat = 45.0, lon = 25.0),
            bbox = RouteBounds(
                minLat = 44.9938,
                minLon = 25.0,
                maxLat = 45.006,
                maxLon = 25.0064
            ),
            segments = listOf(
                "_atqG_yqwCwQ_NwQoU",
                "wasqG_rrwCka@gEka@gE",
                "wasqG_rrwCnFoFnFoF"
            ),
            pointCount = 9
        )

        val decoded = RouteGeometryRepository.decodeRenderableSegments(entry)

        assertEquals(2, decoded.size)
        assertEquals(3, decoded[0].size)
        assertEquals(3, decoded[1].size)
    }
}
