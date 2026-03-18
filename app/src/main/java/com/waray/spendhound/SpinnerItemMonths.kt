package com.waray.spendhound;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class SpinnerItemMonths extends ArrayAdapter<String> {

    private final LayoutInflater mInflater;

    public SpinnerItemMonths(Context context, List<String> items) {
        super(context, R.layout.spinner_item_months, items);
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createItemView(position, convertView, parent);
    }

    private View createItemView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.spinner_item_months, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text2);
        textView.setText(getItem(position));

        return convertView;
    }
}

