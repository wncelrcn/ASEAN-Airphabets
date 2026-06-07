package com.example.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
// Import all DAOs
import com.example.app.data.dao.UserDao
import com.example.app.data.dao.WordDao
import com.example.app.data.dao.ClassDao
import com.example.app.data.dao.EnrollmentDao
import com.example.app.data.dao.StudentDao
import com.example.app.data.dao.ActivityDao
import com.example.app.data.dao.SetDao
import com.example.app.data.dao.SetWordDao
import com.example.app.data.dao.StudentTeacherDao
import com.example.app.data.dao.LearnerProfileAnnotationDao
import com.example.app.data.dao.StudentSetProgressDao
import com.example.app.data.dao.AnnotationSummaryDao
import com.example.app.data.dao.TutorialCompletionDao
import com.example.app.data.dao.ActivityDescriptionCacheDao
import com.example.app.data.dao.KuuRecommendationDao
import com.example.app.data.dao.VideoTutorialDao
// Import all Entities
import com.example.app.data.entity.User
import com.example.app.data.entity.Word
import com.example.app.data.entity.Class
import com.example.app.data.entity.Enrollment
import com.example.app.data.entity.Student
import com.example.app.data.entity.Activity
import com.example.app.data.entity.Set
import com.example.app.data.entity.SetWord
import com.example.app.data.entity.ActivitySet
import com.example.app.data.entity.StudentTeacher
import com.example.app.data.entity.LearnerProfileAnnotation
import com.example.app.data.entity.StudentSetProgress
import com.example.app.data.entity.AnnotationSummary
import com.example.app.data.entity.TutorialCompletion
import com.example.app.data.entity.ActivityDescriptionCache
import com.example.app.data.entity.KuuRecommendation
import com.example.app.data.entity.VideoTutorial

/**
 * Room Database for the Kusho application.
 * Combined version including Classroom, Activities, and Sets.
 */
@Database(
    entities = [
        User::class,
        Word::class,
        Student::class,
        Class::class,
        Enrollment::class,
        Activity::class,
        Set::class,
        SetWord::class,
        ActivitySet::class,
        StudentTeacher::class,
        LearnerProfileAnnotation::class,
        StudentSetProgress::class,
        AnnotationSummary::class,
        TutorialCompletion::class,
        ActivityDescriptionCache::class,
        KuuRecommendation::class,
        VideoTutorial::class
    ],
    version = 19, // Added VideoTutorial table
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // DAOs from both branches
    abstract fun userDao(): UserDao
    abstract fun wordDao(): WordDao
    abstract fun studentDao(): StudentDao
    abstract fun classDao(): ClassDao
    abstract fun enrollmentDao(): EnrollmentDao
    abstract fun activityDao(): ActivityDao
    abstract fun setDao(): SetDao
    abstract fun setWordDao(): SetWordDao
    abstract fun studentTeacherDao(): StudentTeacherDao
    abstract fun learnerProfileAnnotationDao(): LearnerProfileAnnotationDao
    abstract fun studentSetProgressDao(): StudentSetProgressDao
    abstract fun annotationSummaryDao(): AnnotationSummaryDao
    abstract fun tutorialCompletionDao(): TutorialCompletionDao
    abstract fun activityDescriptionCacheDao(): ActivityDescriptionCacheDao
    abstract fun kuuRecommendationDao(): KuuRecommendationDao
    abstract fun videoTutorialDao(): VideoTutorialDao

    companion object {
        private const val DATABASE_NAME = "airphabets_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
            // Added temporarily to avoid crashes while testing combined schema
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}