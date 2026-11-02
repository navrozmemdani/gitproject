package com.visualtrace.ai.features

import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.MediaSelection
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ProviderCatalog
import com.visualtrace.ai.domain.model.ProviderCategory
import com.visualtrace.ai.domain.model.ProviderInfo
import com.visualtrace.ai.domain.model.ProviderMode
import com.visualtrace.ai.domain.repository.FavoritesRepository
import com.visualtrace.ai.domain.repository.HistoryRepository
import com.visualtrace.ai.domain.repository.MediaRepository
import com.visualtrace.ai.domain.repository.ProviderRepository
import com.visualtrace.ai.features.home.HomeViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val catalog = ProviderCatalog(false, "opencv", listOf(
        ProviderInfo("google_cse", "Google", "Google", ProviderCategory.WEB, false, false, true, ProviderMode.LIVE, "", "", true, false, false),
        ProviderInfo("tineye", "TinEye", "TinEye", ProviderCategory.REVERSE_IMAGE, true, true, true, ProviderMode.LIVE, "", "", true, false, false),
        ProviderInfo("bing_visual", "Bing", "Bing", ProviderCategory.REVERSE_IMAGE, true, true, false, ProviderMode.UNAVAILABLE, "", "", false, false, false),
    ))
    private lateinit var settings: FakeSettings
    private lateinit var queue: FakeQueue
    private lateinit var connectivity: FakeConnectivity
    private val media = mockk<MediaRepository>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        settings = FakeSettings(); queue = FakeQueue(); connectivity = FakeConnectivity()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(): HomeViewModel {
        val providers = mockk<ProviderRepository> { coEvery { catalog(any()) } returns Outcome.Success(catalog) }
        val history = mockk<HistoryRepository> { every { observe(any()) } returns flowOf(emptyList()) }
        val favorites = mockk<FavoritesRepository> { every { observeSaved(any()) } returns flowOf(emptyList()) }
        return HomeViewModel(media, providers, settings, queue, connectivity, history, favorites)
    }

    @Test fun `pasted url is validated`() {
        val vm = vm()
        assertFalse(vm.onImageUrl("not a url"))
        assertTrue(vm.state.value.error is AppError.Validation)
        assertTrue(vm.onImageUrl("https://example.com/cat.png"))
        assertTrue(vm.state.value.selection!!.isRemoteUrl)
    }

    @Test fun `unsupported file reports error`() = runTest {
        coEvery { media.inspect("content://gif") } returns Outcome.Failure(AppError.UnsupportedFormat("image/gif"))
        val vm = vm()
        vm.onMediaPicked("content://gif")
        assertTrue(vm.state.value.error is AppError.UnsupportedFormat)
        assertNull(vm.state.value.selection)
    }

    @Test fun `offline search is blocked with a clear error`() {
        connectivity.online = false
        val vm = vm()
        vm.onQueryChange("red car")
        vm.onSearch()
        assertEquals(AppError.NoInternet, vm.state.value.error)
        assertTrue(queue.requests.isEmpty())
    }

    @Test fun `consent is asked before sending images to upload providers`() = runTest {
        coEvery { media.inspect("content://img") } returns Outcome.Success(MediaSelection("content://img", MediaKind.IMAGE, "image/jpeg", "a.jpg", 10))
        val vm = vm()
        vm.enabledProviderKeys.value // start collecting
        vm.onMediaPicked("content://img")
        vm.onSearch()
        assertTrue(vm.state.value.showConsentDialog)
        assertTrue(queue.requests.isEmpty())
        vm.onConsentAnswer(false)
        val req = queue.requests.single()
        assertFalse(req.thirdPartyConsent)
        assertEquals(listOf("google_cse", "tineye"), req.providers) // defaults only; unconfigured bing is not default
    }

    @Test fun `text only search uses saved provider preferences`() {
        settings.state.value = AppSettings(enabledProviders = setOf("google_cse"), consentPromptAnswered = true)
        val vm = vm()
        vm.enabledProviderKeys.value
        vm.onQueryChange("red sports car")
        vm.onSearch()
        val req = queue.requests.single()
        assertNull(req.media)
        assertEquals("red sports car", req.queryText)
        assertEquals(listOf("google_cse"), req.providers)
    }

    @Test fun `full video upload requires explicit consent`() {
        val vm = vm()
        vm.onFullVideoToggle(true)
        assertTrue(vm.state.value.showFullVideoDialog)
        assertFalse(vm.state.value.uploadFullVideo)
        vm.onFullVideoConsent(true)
        assertTrue(vm.state.value.uploadFullVideo)
        assertTrue(settings.state.value.fullVideoUploadConsent)
    }
}
