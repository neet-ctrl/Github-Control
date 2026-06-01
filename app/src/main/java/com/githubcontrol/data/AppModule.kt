package com.githubcontrol.data

import android.content.Context
import androidx.room.Room
import com.githubcontrol.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "githubcontrol.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideCommandDao(db: AppDatabase)  = db.commandHistory()
    @Provides fun provideUploadDao(db: AppDatabase)   = db.uploadHistory()
    @Provides fun provideSyncDao(db: AppDatabase)     = db.syncJobs()
    @Provides fun provideDownloadDao(db: AppDatabase) = db.downloads()
    @Provides fun provideSnippetDao(db: AppDatabase)  = db.snippets()
}
