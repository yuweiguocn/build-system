package com.example.android.multiproject.libfeat;

import android.content.Context;
import android.widget.TextView;

class LibFeatView extends TextView {
    public LibFeatView(Context context, String name) {
        super(context);
        setTextSize(20);
        setText(name);
    }
}
