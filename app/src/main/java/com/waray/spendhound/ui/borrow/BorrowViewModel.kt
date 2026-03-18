package com.waray.spendhound.ui.borrow

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BorrowViewModel : ViewModel() {
    private val mText: MutableLiveData<String?>

    init {
        mText = MutableLiveData<String?>()
        mText.setValue("This is dashboard fragment")
    }

    val text: LiveData<String?>
        get() = mText
}