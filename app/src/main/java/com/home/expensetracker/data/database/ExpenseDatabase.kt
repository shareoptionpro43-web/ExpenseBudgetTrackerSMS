package com.home.expensetracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.home.expensetracker.data.models.Budget
import com.home.expensetracker.data.models.Category
import com.home.expensetracker.data.models.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Expense::class, Budget::class, Category::class],
    version = 1,
    exportSchema = true
)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var INSTANCE: ExpenseDatabase? = null

        fun getInstance(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_tracker.db"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDefaultCategories(database.categoryDao())
                    }
                }
            }
        }

        private suspend fun populateDefaultCategories(categoryDao: CategoryDao) {
            val defaults = listOf(
                Category(name = "Food & Dining",    icon = "🍽️", colorHex = "#FF6B6B", isDefault = true),
                Category(name = "Groceries",        icon = "🛒", colorHex = "#4ECDC4", isDefault = true),
                Category(name = "Transportation",   icon = "🚗", colorHex = "#45B7D1", isDefault = true),
                Category(name = "Utilities",        icon = "💡", colorHex = "#F7DC6F", isDefault = true),
                Category(name = "Healthcare",       icon = "🏥", colorHex = "#BB8FCE", isDefault = true),
                Category(name = "Entertainment",    icon = "🎬", colorHex = "#F0B27A", isDefault = true),
                Category(name = "Shopping",         icon = "🛍️", colorHex = "#82E0AA", isDefault = true),
                Category(name = "Education",        icon = "📚", colorHex = "#85C1E9", isDefault = true),
                Category(name = "Home & Rent",      icon = "🏠", colorHex = "#F1948A", isDefault = true),
                Category(name = "Insurance",        icon = "🛡️", colorHex = "#A9CCE3", isDefault = true),
                Category(name = "Savings",          icon = "💰", colorHex = "#A9DFBF", isDefault = true),
                Category(name = "Personal Care",    icon = "💅", colorHex = "#F9E79F", isDefault = true),
                Category(name = "Kids",             icon = "👶", colorHex = "#FAD7A0", isDefault = true),
                Category(name = "Pets",             icon = "🐾", colorHex = "#D2B4DE", isDefault = true),
                Category(name = "Travel",           icon = "✈️", colorHex = "#AED6F1", isDefault = true),
                Category(name = "Other",            icon = "📦", colorHex = "#BDC3C7", isDefault = true)
            )
            categoryDao.insertCategories(defaults)
        }
    }
}
