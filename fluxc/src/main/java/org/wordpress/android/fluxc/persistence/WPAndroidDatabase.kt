package org.wordpress.android.fluxc.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.wordpress.android.fluxc.persistence.BloggingRemindersDao.BloggingReminders
import org.wordpress.android.fluxc.persistence.PlanOffersDao.PlanOffer
import org.wordpress.android.fluxc.persistence.PlanOffersDao.PlanOfferFeature
import org.wordpress.android.fluxc.persistence.PlanOffersDao.PlanOfferId
import org.wordpress.android.fluxc.persistence.comments.CommentsDao
import org.wordpress.android.fluxc.persistence.comments.CommentsDao.CommentEntity

@Database(
        version = 4,
        entities = [
            BloggingReminders::class,
            PlanOffer::class,
            PlanOfferId::class,
            PlanOfferFeature::class,
            CommentEntity::class
        ]
)
abstract class WPAndroidDatabase : RoomDatabase() {
    abstract fun bloggingRemindersDao(): BloggingRemindersDao

    abstract fun planOffersDao(): PlanOffersDao

    abstract fun commentsDao(): CommentsDao

    companion object {
        const val WP_DB_NAME = "wp-android-database"

        fun buildDb(applicationContext: Context) = Room.databaseBuilder(
                applicationContext,
                WPAndroidDatabase::class.java,
                WP_DB_NAME
        )
                .fallbackToDestructiveMigration()
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                            "CREATE TABLE IF NOT EXISTS `PlanOffers` (" +
                                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "`internalPlanId` INTEGER NOT NULL, " +
                                    "`name` TEXT, " +
                                    "`shortName` TEXT, " +
                                    "`tagline` TEXT, " +
                                    "`description` TEXT, " +
                                    "`icon` TEXT" +
                                    ")"
                    )
                    execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS `index_PlanOffers_internalPlanId` " +
                                    "ON `PlanOffers` (`internalPlanId`)"
                    )
                    execSQL(
                            "CREATE TABLE IF NOT EXISTS `PlanOfferIds` (" +
                                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "`productId` INTEGER NOT NULL, " +
                                    "`internalPlanId` INTEGER NOT NULL, " +
                                    "FOREIGN KEY(`internalPlanId`) REFERENCES `PlanOffers`(`internalPlanId`) " +
                                    "ON UPDATE NO ACTION ON DELETE CASCADE" +
                                    ")"
                    )
                    execSQL(
                            "CREATE TABLE IF NOT EXISTS `PlanOfferFeatures` (" +
                                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "`internalPlanId` INTEGER NOT NULL, " +
                                    "`stringId` TEXT, " +
                                    "`name` TEXT, " +
                                    "`description` TEXT, " +
                                    "FOREIGN KEY(`internalPlanId`) REFERENCES `PlanOffers`(`internalPlanId`) " +
                                    "ON UPDATE NO ACTION ON DELETE CASCADE" +
                                    ")"
                    )
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                            "CREATE TABLE IF NOT EXISTS `Comments` (" +
                                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "`remoteCommentId` INTEGER NOT NULL, " +
                                    "`remotePostId` INTEGER NOT NULL, " +
                                    "`remoteParentCommentId` INTEGER NOT NULL, " +
                                    "`localSiteId` INTEGER NOT NULL, " +
                                    "`remoteSiteId` INTEGER NOT NULL, " +
                                    "`authorUrl` TEXT, " +
                                    "`authorName` TEXT, " +
                                    "`authorEmail` TEXT, " +
                                    "`authorProfileImageUrl` TEXT, " +
                                    "`postTitle` TEXT, " +
                                    "`status` TEXT, " +
                                    "`datePublished` TEXT, " +
                                    "`publishedTimestamp` INTEGER NOT NULL, " +
                                    "`content` TEXT, " +
                                    "`url` TEXT, " +
                                    "`hasParent` INTEGER NOT NULL, " +
                                    "`parentId` INTEGER NOT NULL, " +
                                    "`iLike` INTEGER NOT NULL)"
                    )
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("ALTER TABLE BloggingReminders ADD COLUMN hour INTEGER DEFAULT 10 NOT NULL")
                    execSQL("ALTER TABLE BloggingReminders ADD COLUMN minute INTEGER DEFAULT 0 NOT NULL")
                }
            }
        }
    }
}
