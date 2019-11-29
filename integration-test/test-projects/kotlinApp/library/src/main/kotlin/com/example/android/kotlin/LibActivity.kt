package com.example.android.kotlin

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.android.kotlin.lib.R

class LibActivity : Activity() {

    var text: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lib_activity_layout)
        val tmpTxt: View = findViewById(R.id.someText)
        text = tmpTxt as TextView
        if (text != null) {
            text?.setText("testing kotlin")
            android.util.Log.d("kotlin", text?.getText().toString())
        }
        val tmpClick: View = findViewById(R.id.click)
        val click = tmpClick as Button
        click?.setOnClickListener { text?.setText("clicked!") }
    }
}
