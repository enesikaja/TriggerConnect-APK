package com.triggerconnect.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.triggerconnect.app.data.local.AppDatabase
import com.triggerconnect.app.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun contactDao_replaceAll_thenObserve_returnsAcceptedOnly() = runBlocking {
        val dao = db.contactDao()
        dao.replaceAll(
            listOf(
                ContactEntity("c1", "u1", "Sam", "ACCEPTED"),
                ContactEntity("c2", "u2", "Jo", "ACCEPTED"),
                ContactEntity("c3", "u3", "Blocked", "BLOCKED"),
            ),
        )
        val contacts = dao.observeContacts().first()
        assertEquals(2, contacts.size)
        assertEquals(listOf("Jo", "Sam"), contacts.map { it.displayName }) // ordered by name
    }
}
