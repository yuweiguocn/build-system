package com.example.android.lint.kotlin;

import com.example.android.lint.kotlin.library.BaseFragment;

public class SampleFragment extends BaseFragment {
    public SampleFragment(String foo) { // Deliberate lint error
    }
}
