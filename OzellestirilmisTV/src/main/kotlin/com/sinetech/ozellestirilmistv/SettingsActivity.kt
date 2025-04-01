package com.sinetech.latte

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sinetech.common.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO: SharedPreferences üzerinden kayıtlı listeleri yükle
        // TODO: RecyclerView adapter ile liste yönetimi
    }

    private fun saveSelectedLists() {
        // TODO: Seçilen listeleri kaydetme mantığı
    }
}