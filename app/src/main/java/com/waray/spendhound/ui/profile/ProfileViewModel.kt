package com.waray.spendhound.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileViewModel : ViewModel() {
    private val mText: MutableLiveData<String?>

    init {
        mText = MutableLiveData<String?>()
        mText.setValue("This is notifications fragment")
    }

    val text: LiveData<String?>
        get() = mText
}