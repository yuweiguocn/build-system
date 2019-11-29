package com.example.android.kotlin

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.example.android.kotlin.databinding.ActivityLayoutBinding

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding : ActivityLayoutBinding = ActivityLayoutBinding
                .inflate(LayoutInflater.from(this))!!
        setContentView(binding.root)
        binding.model = ViewModel("foo", "bar")
    }
}
