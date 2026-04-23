package com.waray.spendhound

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView

/**
 * Drop-in skeleton adapter. Show it while loading, swap with the real adapter when data arrives.
 *
 * Usage:
 *   rv.adapter = SkeletonAdapter(R.layout.item_skeleton_transaction)
 *   // data arrives →
 *   rv.adapter = realAdapter
 */
class SkeletonAdapter(
    @LayoutRes private val skeletonLayout: Int,
    private val itemCount: Int = 6
) : RecyclerView.Adapter<SkeletonAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(skeletonLayout, parent, false))

    override fun getItemCount() = itemCount
    override fun onBindViewHolder(holder: VH, position: Int) = Unit
}
