package com.visualtrace.ai.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.visualtrace.ai.data.local.CollectionEntity
import com.visualtrace.ai.data.local.HistoryEntity
import com.visualtrace.ai.data.local.SavedResultEntity
import com.visualtrace.ai.data.local.VisualTraceDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: VisualTraceDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VisualTraceDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun `history is ordered newest first and filterable`() = runTest {
        val dao = db.historyDao()
        dao.upsert(HistoryEntity("a", "IMAGE", "COMPLETED", 1, 3, "first", null))
        dao.upsert(HistoryEntity("b", "VIDEO", "PARTIAL", 2, 1, "second", null))
        dao.observe(null).test { assertEquals(listOf("b", "a"), awaitItem().map { it.searchId }); cancel() }
        dao.observe("VIDEO").test { assertEquals(listOf("b"), awaitItem().map { it.searchId }); cancel() }
        dao.delete("b")
        dao.observe(null).test { assertEquals(listOf("a"), awaitItem().map { it.searchId }); cancel() }
        dao.clear()
        dao.observe(null).test { assertTrue(awaitItem().isEmpty()); cancel() }
    }

    @Test fun `collections count items and detach on delete`() = runTest {
        val dao = db.favoritesDao()
        dao.insertCollection(CollectionEntity("c1", "Cars", 1))
        dao.insert(SavedResultEntity("s1", "r1", "sid", "{}", "c1", "note", 1, null))
        dao.insert(SavedResultEntity("s2", "r2", "sid", "{}", null, null, 2, "remote-2"))
        dao.observeCollections().test { assertEquals(1, awaitItem().single().itemCount); cancel() }
        dao.isSaved("r1").test { assertTrue(awaitItem()); cancel() }
        assertEquals(listOf("s1"), dao.unsynced().map { it.id })
        dao.detachCollection("c1"); dao.deleteCollection("c1")
        assertEquals(null, dao.get("s1")!!.collectionId)
        dao.delete("s1")
        dao.isSaved("r1").test { assertFalse(awaitItem()); cancel() }
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun `collection names are unique`() = runTest {
        db.favoritesDao().insertCollection(CollectionEntity("c1", "Cars", 1))
        db.favoritesDao().insertCollection(CollectionEntity("c2", "Cars", 2))
    }
}
