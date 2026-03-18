package com.waray.spendhound

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class SpinnerItem(context: Context, items: MutableList<String?>) :
    ArrayAdapter<String?>(context, R.layout.spinner_item, items) {
    private val mInflater: LayoutInflater

    init {
        mInflater = LayoutInflater.from(context)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return createItemView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return createItemView(position, convertView, parent)
    }

    private fun createItemView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var convertView = convertView
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.spinner_item, parent, false)
        }

        val textView = convertView.findViewById<TextView>(android.R.id.text1)
        textView.setText(getItem(position))

        return convertView
    }
}

