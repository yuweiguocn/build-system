package com.example.android.kotlin

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.example.android.kotlin.lib.R
import com.example.android.kotlin.lib.databinding.LibActivityLayoutBinding

class LibActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // need !! because kotlin compiler does not understand the new nullability annotations
        val binding : LibActivityLayoutBinding = LibActivityLayoutBinding
                .inflate(LayoutInflater.from(this))!!
        setContentView(binding.root)
        binding.model = ViewModel("foo", "bar")
    }
}
