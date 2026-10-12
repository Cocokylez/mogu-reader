package com.tome.reader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        SeriesEntity::class,
        OnlineHistoryEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun collectionDao(): CollectionDao
    abstract fun seriesDao(): SeriesDao
    abstract fun onlineHistoryDao(): OnlineHistoryDao

    companion object {

        /**
         * v1 -> v2: adds the `series` table and the chapter-link columns on
         * `books`. Existing books migrate untouched (seriesId stays NULL, so they
         * remain standalone). No data is lost.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `series` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`coverPath` TEXT, " +
                        "`kind` TEXT NOT NULL, " +
                        "`sourceUri` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_series_sourceUri` ON `series` (`sourceUri`)"
                )
                db.execSQL("ALTER TABLE `books` ADD COLUMN `seriesId` INTEGER")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `seriesOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_seriesId` ON `books` (`seriesId`)")
            }
        }

        /**
         * v2 -> v3: adds the nullable `accentColor` column to `books` and `series`
         * for cover-derived UI tinting. Existing rows get NULL (no tint) and are
         * coloured the next time their cover is (re)generated.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `accentColor` INTEGER")
                db.execSQL("ALTER TABLE `series` ADD COLUMN `accentColor` INTEGER")
            }
        }

        /**
         * v3 -> v4: adds the `online_history` table that remembers the reading
         * position of streamed (online) content so it shows in History and resumes.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `online_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`scraperId` TEXT NOT NULL, " +
                        "`seriesUrl` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`author` TEXT, " +
                        "`coverUrl` TEXT, " +
                        "`category` TEXT NOT NULL, " +
                        "`sourceName` TEXT NOT NULL, " +
                        "`chapterIndex` INTEGER NOT NULL, " +
                        "`chapterTitle` TEXT, " +
                        "`scrollItem` INTEGER NOT NULL, " +
                        "`scrollOffset` INTEGER NOT NULL, " +
                        "`lastReadAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_online_history_scraperId_seriesUrl` " +
                        "ON `online_history` (`scraperId`, `seriesUrl`)"
                )
            }
        }

        /**
         * v4 -> v5: stores streamed online chapters that have been passed/read,
         * so online unread downloads can skip them.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `online_history` ADD COLUMN `readChapterIndices` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tome.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
    }
}
