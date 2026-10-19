package dev.robotvision.di

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.robotvision.data.local.*
import dev.robotvision.data.settings.UserSettings
import dev.robotvision.data.speech.*
import dev.robotvision.domain.*
import dev.robotvision.domain.Target
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = object : Clock {
        override fun now() = SystemClock.elapsedRealtime()
        override fun wallTime() = System.currentTimeMillis()
    }
    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun computation(): CoroutineDispatcher = Dispatchers.Default
    @Provides @Singleton fun database(@ApplicationContext context: Context) =
        Room.databaseBuilder(context, EventDatabase::class.java, "robot-vision.db").build()
    @Provides fun dao(database: EventDatabase) = database.events()
    @Provides @Singleton fun events(dao: EventDao): EventRepository = RoomEventRepository(dao)
    @Provides @Singleton fun settings(@ApplicationContext context: Context): SettingsRepository = UserSettings(context)
    @Provides fun speech(engine: SherpaOnnxSpeechRecognizer): SpeechRecognizerEngine = engine
    @Provides fun audio(input: AudioRecordInput): AudioInput = input
}
