package com.example.android.multiproject.base;

import android.content.Context;
import android.widget.TextView;

class PersonView2 extends TextView {
    public PersonView2(Context context, String name) {
        super(context);
        setTextSize(20);
        setText(name);
    }
}
