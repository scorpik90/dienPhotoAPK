package ru.delivery.photo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        DeliveryPointEntity::class,
        CrewMemberEntity::class,
        PhotoEntity::class,
        PackageEntity::class,
        SettingEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun deliveryPointDao(): DeliveryPointDao
    abstract fun crewDao(): CrewDao
    abstract fun photoDao(): PhotoDao
    abstract fun packageDao(): PackageDao
    abstract fun settingDao(): SettingDao

    companion object {
        private const val NAME = "delivery_photo.db"

        /** Добавление экипажа рейса. Данные существующих рейсов сохраняются. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `crew_members` (" +
                        "`id` TEXT NOT NULL, " +
                        "`tripId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`vehicle` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /** Подписание документов больше не используется — удаляем таблицу. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `signoffs`")
            }
        }

        /** Модель оборудования для фото этикетки. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `model` TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
