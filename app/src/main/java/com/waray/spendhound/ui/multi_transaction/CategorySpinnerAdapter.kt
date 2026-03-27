package com.waray.spendhound.ui.multi_transaction

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.waray.spendhound.R

class CategorySpinnerAdapter(context: Context, private val categories: List<String>) :
    ArrayAdapter<String>(context, 0, categories) {

    private fun iconFor(category: String) = when (category) {
        "Electricity" -> R.drawable.lightning_bolt
        "Water" -> R.drawable.faucet
        "Rent" -> R.drawable.house
        "Internet" -> R.drawable.internet
        "Online Shopping" -> R.drawable.online_shopping
        "Travel" -> R.drawable.travel
        "Groceries" -> R.drawable.groceries
        "Foods" -> R.drawable.hamburger
        "House Necessity" -> R.drawable.necessities
        "Transportation" -> R.drawable.vehicles
        else -> R.drawable.others
    }

    private fun getView(position: Int, convertView: View?, parent: ViewGroup, isDropdown: Boolean): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.spinner_item_category, parent, false)
        val category = categories[position]
        view.findViewById<ImageView>(R.id.ivCategoryIcon).setImageResource(iconFor(category))
        view.findViewById<TextView>(R.id.tvCategoryName).text = category
        return view
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
        getView(position, convertView, parent, false)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup) =
        getView(position, convertView, parent, true)
}
