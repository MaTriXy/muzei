/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.muzei.gallery

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database for accessing Gallery data
 */
@Database(entities = [(ChosenPhoto::class), (Metadata::class)], version = 7)
internal abstract class GalleryDatabase : RoomDatabase() {

    companion object {
        @Volatile
        private var instance: GalleryDatabase? = null

        fun getInstance(context: Context): GalleryDatabase =
                instance ?: synchronized(this) {
                    instance ?: buildDatabase(context).also { instance = it }
                }

        private fun buildDatabase(context: Context) =
                Room.databaseBuilder(context.applicationContext, GalleryDatabase::class.java,
                        "gallery_source.db")
                        .addMigrations(
                                MIGRATION_1_2,
                                MIGRATION_2_3,
                                MIGRATION_3_4,
                                MIGRATION_4_5,
                                MIGRATION_5_6,
                                MIGRATION_6_7)
                        .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS metadata_cache")
                db.execSQL("CREATE TABLE metadata_cache ("
                        + "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                        + "uri TEXT NOT NULL,"
                        + "datetime INTEGER,"
                        + "location TEXT,"
                        + "UNIQUE (uri) ON CONFLICT REPLACE)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chosen_photos" + " ADD COLUMN is_tree_uri INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Due to an issue with upgrading version 2 to 3, some users might have the
                // COLUMN_NAME_IS_TREE_URI column and some might not. Awkward.
                // We'll check if the column exists and add it if it doesn't exist
                val pragma = db.query("PRAGMA table_info(chosen_photos)")
                var columnExists = false
                while (pragma.moveToNext()) {
                    val columnIndex = pragma.getColumnIndex("name")
                    if (columnIndex != -1 && pragma.getString(columnIndex) == "is_tree_uri") {
                        columnExists = true
                    }
                }
                pragma.close()
                if (!columnExists) {
                    db.execSQL("ALTER TABLE chosen_photos" + " ADD COLUMN is_tree_uri INTEGER")
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // NO-OP
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Handle Chosen Photos
                db.execSQL("UPDATE chosen_photos "
                        + "SET is_tree_uri = 0 "
                        + "WHERE is_tree_uri IS NULL")
                db.execSQL("CREATE TABLE chosen_photos2 ("
                        + "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                        + "uri TEXT NOT NULL,"
                        + "is_tree_uri INTEGER NOT NULL,"
                        + "UNIQUE (uri) ON CONFLICT REPLACE)")
                db.execSQL("INSERT INTO chosen_photos2 " + "SELECT * FROM chosen_photos")
                db.execSQL("DROP TABLE chosen_photos")
                db.execSQL("ALTER TABLE chosen_photos2 RENAME TO chosen_photos")
                db.execSQL("CREATE UNIQUE INDEX index_chosen_photos_uri " + "ON chosen_photos (uri)")

                // Handle Metadata
                db.execSQL("CREATE TABLE metadata_cache2 ("
                        + "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                        + "uri TEXT NOT NULL,"
                        + "datetime INTEGER,"
                        + "location TEXT,"
                        + "UNIQUE (uri) ON CONFLICT REPLACE)")
                db.execSQL("INSERT INTO metadata_cache2 " + "SELECT * FROM metadata_cache")
                db.execSQL("DROP TABLE metadata_cache")
                db.execSQL("ALTER TABLE metadata_cache2 RENAME TO metadata_cache")
                db.execSQL("CREATE UNIQUE INDEX index_metadata_cache_uri " + "ON metadata_cache (uri)")
            }
        }

        /**
         * Basically an identity migration, but handles issues with the more strict
         * schema checking in Room 2.2.0 (specifically around the UNIQUE (uri) ON CONFLICT REPLACE
         * constraint that was added in MIGRATION_5_6).
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Handle Chosen Photos
                db.execSQL("CREATE TABLE chosen_photos2 ("
                        + "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                        + "uri TEXT NOT NULL,"
                        + "is_tree_uri INTEGER NOT NULL)")
                db.execSQL("INSERT INTO chosen_photos2 " + "SELECT * FROM chosen_photos")
                db.execSQL("DROP TABLE chosen_photos")
                db.execSQL("ALTER TABLE chosen_photos2 RENAME TO chosen_photos")
                db.execSQL("CREATE UNIQUE INDEX index_chosen_photos_uri " + "ON chosen_photos (uri)")

                // Handle Metadata
                db.execSQL("CREATE TABLE metadata_cache2 ("
                        + "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                        + "uri TEXT NOT NULL,"
                        + "datetime INTEGER,"
                        + "location TEXT)")
                db.execSQL("INSERT INTO metadata_cache2 " + "SELECT * FROM metadata_cache")
                db.execSQL("DROP TABLE metadata_cache")
                db.execSQL("ALTER TABLE metadata_cache2 RENAME TO metadata_cache")
                db.execSQL("CREATE UNIQUE INDEX index_metadata_cache_uri " + "ON metadata_cache (uri)")
            }
        }
    }

    internal abstract fun chosenPhotoDao(): ChosenPhotoDao

    internal abstract fun metadataDao(): MetadataDao
}