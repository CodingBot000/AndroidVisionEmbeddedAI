package dev.robotvision.data
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.robotvision.data.local.*
import dev.robotvision.data.settings.UserSettings
import dev.robotvision.domain.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class StorageTest {
    @Test fun saveAndObserveOrdered() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),EventDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository=RoomEventRepository(db.events())
            repository.save(Event(time=10,type="FOUND",label="cup",score=.8f))
            repository.save(Event(time=20,type="NOT_FOUND",label="book"))
            val events=repository.events.first();assertEquals(listOf("book","cup"),events.map{it.label});assertEquals(.8f,events.last().score)
        } finally { db.close() }
    }
    @Test fun clearHistory() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),EventDatabase::class.java).allowMainThreadQueries().build()
        try { val r=RoomEventRepository(db.events());r.save(Event(time=1,type="FOUND"));r.clear();assertTrue(r.events.first().isEmpty()) } finally { db.close() }
    }
    @Test fun datastorePersistsAndClamps() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val settings=UserSettings(context);settings.setConfidence(5f);settings.setHaptic(false)
        val read=UserSettings(context).settings.first();assertEquals(.9f,read.confidence);assertFalse(read.haptic)
    }
}
