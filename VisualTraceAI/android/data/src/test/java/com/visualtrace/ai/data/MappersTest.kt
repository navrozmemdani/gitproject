package com.visualtrace.ai.data

import com.visualtrace.ai.data.remote.ApiJson
import com.visualtrace.ai.data.remote.dto.ResultsPageDto
import com.visualtrace.ai.data.remote.dto.SearchDetailDto
import com.visualtrace.ai.data.remote.parseInstant
import com.visualtrace.ai.data.remote.toDomain
import com.visualtrace.ai.data.remote.toDto
import com.visualtrace.ai.domain.model.MatchType
import com.visualtrace.ai.domain.model.Provenance
import com.visualtrace.ai.domain.model.ProviderRunStatus
import com.visualtrace.ai.domain.model.ResultCategory
import com.visualtrace.ai.domain.model.ResultFilter
import com.visualtrace.ai.domain.model.SearchStatus
import com.visualtrace.ai.domain.model.SearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun resource(name: String): String = requireNotNull(object {}.javaClass.classLoader).getResource(name)!!.readText()

class MappersTest {
    @Test fun `search detail maps analysis provenance and unknown statuses`() {
        val s = ApiJson.decodeFromString(SearchDetailDto.serializer(), resource("search_detail.json")).toDomain()
        assertEquals(SearchType.IMAGE, s.type)
        assertEquals(SearchStatus.PARTIAL, s.status)
        assertEquals(640, s.mediaWidth)
        assertEquals(1f, s.progress)
        assertEquals(1, s.providersSearched.size)
        assertEquals(ProviderRunStatus.SKIPPED_NO_CONSENT, s.providerReports[1].status)
        assertEquals(ProviderRunStatus.UNKNOWN, s.providerReports[2].status)
        val a = s.analysis!!
        assertTrue(a.objects.isInferred)
        assertEquals("sports car", a.objects.items.single().label)
        assertFalse(a.environment.isAvailable)
        assertEquals("VT-2026", a.ocrText)
        assertEquals("#be2121", a.colors.items.first().hex)
        assertEquals(listOf("red sports car", "sports car"), a.keywords)
        assertFalse(s.usedMockData)
    }

    @Test fun `results page maps categories, provenance, via and mock flags`() {
        val page = ApiJson.decodeFromString(ResultsPageDto.serializer(), resource("results_page.json")).toDomain()
        assertEquals(2, page.items.size)
        assertEquals(1, page.counts[ResultFilter.EXACT])
        val r1 = page.items[0]
        assertEquals(MatchType.EXACT, r1.matchType)
        assertEquals(ResultCategory.WEBSITE, r1.category)
        assertEquals(Provenance.PROVIDER, r1.provenanceOf("page_title"))
        assertEquals(Provenance.MEASURED, r1.provenanceOf("similarity_score"))
        assertEquals(Provenance.UNAVAILABLE, r1.provenanceOf("author_username"))
        assertEquals("Google Programmable Search", r1.via)
        assertNull(r1.provenance["via"])
        val r2 = page.items[1]
        assertEquals(MatchType.CROPPED, r2.matchType)
        assertTrue(r2.isMock)
        assertEquals(3000L, r2.frameTimestampMs)
        assertEquals(1767225600000L, r2.publishedAtEpochMs)
    }

    @Test fun `domain to dto round trip keeps data for the offline cache`() {
        val page = ApiJson.decodeFromString(ResultsPageDto.serializer(), resource("results_page.json")).toDomain()
        val again = ApiJson.decodeFromString(ResultsPageDto.serializer(),
            ApiJson.encodeToString(ResultsPageDto.serializer(), page.toDto())).toDomain()
        assertEquals(page.items, again.items)
        assertEquals(page.counts, again.counts)
    }

    @Test fun `instant parsing handles offsets and garbage`() {
        assertEquals(1767225600000L, parseInstant("2026-01-01T00:00:00+00:00"))
        assertEquals(1767225600123L, parseInstant("2026-01-01T00:00:00.123Z"))
        assertNull(parseInstant("yesterday"))
        assertNull(parseInstant(null))
    }
}
