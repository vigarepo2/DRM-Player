package com.dokubots.drmplayer

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dokubots.drmplayer.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val history = HistoryManager.getHistory(this)
        if (history.isEmpty()) {
            Toast.makeText(this, "No history found", Toast.LENGTH_SHORT).show()
            return
        }

        val displayList = history.map { "${it.title}\n${it.fullUrl.take(50)}..." }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        binding.listViewHistory.adapter = adapter

        binding.listViewHistory.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("URL", history[position].fullUrl)
            }
            startActivity(intent)
        }
    }
}
