package com.waray.spendhound.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val mText: MutableLiveData<String?>

    init {
        mText = MutableLiveData<String?>()
        mText.setValue("This is home fragment")
    }

    val text: LiveData<String?>
        get() = mText
}