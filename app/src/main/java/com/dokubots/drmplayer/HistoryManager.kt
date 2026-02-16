package com.dokubots.drmplayer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class HistoryItem(val title: String, val fullUrl: String, val timestamp: Long)

object HistoryManager {
    private const val PREFS = "doku_history"
    private const val KEY = "history_list"

    fun saveHistory(context: Context, fullUrl: String, title: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(KEY, "[]")
        val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
        val history: MutableList<HistoryItem> = gson.fromJson(json, type) ?: mutableListOf()

        history.removeAll { it.fullUrl == fullUrl }
        history.add(0, HistoryItem(title, fullUrl, System.currentTimeMillis()))

        if (history.size > 50) history.removeLast() 
        
        prefs.edit().putString(KEY, gson.toJson(history)).apply()
    }

    fun getHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]")
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        return Gson().fromJson(json, type) ?: emptyList()
    }
}
