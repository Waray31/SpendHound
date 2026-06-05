package com.waray.spendhound

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation

class LenderChipAdapter(
    private val lenders: List<User>,
    private var selectedLenderId: Long? = null,
    private val onLenderSelected: (User) -> Unit
) : RecyclerView.Adapter<LenderChipAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.chipContainer)
        val cvInitial: CardView = view.findViewById(R.id.cvInitial)
        val tvInitial: TextView = view.findViewById(R.id.tvInitial)
        val ivProfile: ImageView = view.findViewById(R.id.ivProfile)
        val tvName: TextView = view.findViewById(R.id.tvName)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val lender = lenders[position]
                    val oldSelectedId = selectedLenderId
                    selectedLenderId = lender.id
                    
                    // Notify changes for the previously and newly selected items
                    val oldIndex = lenders.indexOfFirst { it.id == oldSelectedId }
                    if (oldIndex != -1) notifyItemChanged(oldIndex)
                    notifyItemChanged(position)
                    
                    onLenderSelected(lender)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payor_chip_dark, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lender = lenders[position]
        holder.tvName.text = lender.username
        
        val initials = lender.username?.take(2)?.uppercase() ?: "?"
        holder.tvInitial.text = initials

        // Reset state: show initials, clear and show image view (transparent)
        holder.tvInitial.visibility = View.VISIBLE
        holder.ivProfile.visibility = View.VISIBLE
        holder.ivProfile.setImageDrawable(null)

        val profileUrl = lender.profileImageUrl
        val finalUrl = if (!profileUrl.isNullOrBlank() && profileUrl != "placeholder_profile_image") {
            profileUrl
        } else {
            lender.id?.let { DeclareDatabase.profileImagesBucket.publicUrl("$it/$it.jpg") }
        }

        if (!finalUrl.isNullOrBlank()) {
            holder.ivProfile.load(finalUrl) {
                transformations(CircleCropTransformation())
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        holder.tvInitial.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        holder.tvInitial.visibility = View.VISIBLE
                        holder.ivProfile.visibility = View.GONE
                    }
                )
            }
        } else {
            holder.ivProfile.visibility = View.GONE
        }

        val isSelected = lender.id == selectedLenderId
        
        holder.container.setBackgroundResource(
            if (isSelected) R.drawable.bg_dark_chip_selected 
            else R.drawable.bg_light_chip_outline
        )
        
        holder.tvName.setTextColor(if (isSelected) 0xFF000000.toInt() else 0xFF757575.toInt())
    }

    override fun getItemCount(): Int = lenders.size

    fun updateSelectedLender(lenderId: Long?) {
        val oldSelectedId = selectedLenderId
        selectedLenderId = lenderId
        
        val oldIndex = lenders.indexOfFirst { it.id == oldSelectedId }
        if (oldIndex != -1) notifyItemChanged(oldIndex)
        
        val newIndex = lenders.indexOfFirst { it.id == lenderId }
        if (newIndex != -1) notifyItemChanged(newIndex)
    }
}
