package com.waray.spendhound.ui.multi_transaction

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.waray.spendhound.R
import com.waray.spendhound.databinding.ItemTransactionMultiBinding

class MultiTransactionAdapter(
    private val onAmountChanged: () -> Unit,
    private val onPayorClick: (Int) -> Unit
) : RecyclerView.Adapter<MultiTransactionAdapter.TransactionViewHolder>() {

    private val transactions = mutableListOf<TransactionEntry>()
    private var isMultiplePayorsMode = false

    fun setTransactions(newTransactions: List<TransactionEntry>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }

    fun setMode(isMultiple: Boolean) {
        isMultiplePayorsMode = isMultiple
        notifyDataSetChanged()
    }

    fun addRow(entry: TransactionEntry) {
        transactions.add(entry)
        notifyItemInserted(transactions.size - 1)
    }

    fun removeRow(position: Int) {
        if (position in transactions.indices) {
            transactions.removeAt(position)
            notifyItemRemoved(position)
            onAmountChanged()
        }
    }

    fun getTransactions() = transactions

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionMultiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount() = transactions.size

    inner class TransactionViewHolder(private val binding: ItemTransactionMultiBinding) : RecyclerView.ViewHolder(binding.root) {
        
        private var currentWatcher: TextWatcher? = null

        fun bind(entry: TransactionEntry) {
            binding.etTitle.setText(entry.title)
            binding.etAmount.setText(if (entry.amount > 0) entry.amount.toString() else "")
            
            binding.layoutPayerRow.isVisible = isMultiplePayorsMode
            
            // Setup Category Spinner
            val categories = listOf("General", "Food", "Transport", "Bills", "Entertainment")
            val adapter = ArrayAdapter(itemView.context, android.R.layout.simple_spinner_item, categories)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerCategory.adapter = adapter
            binding.spinnerCategory.setSelection(categories.indexOf(entry.category).coerceAtLeast(0))

            binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    entry.category = categories[pos]
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Payor Text
            if (isMultiplePayorsMode) {
                val payorText = when {
                    entry.payors.isEmpty() -> "Select Payor"
                    entry.payors.size == 1 -> entry.payors[0].username
                    else -> "${entry.payors[0].username} + ${entry.payors.size - 1} others"
                }
                binding.tvPaidBy.text = payorText
                binding.tvPaidBy.setOnClickListener { onPayorClick(adapterPosition) }
            }

            // Amount Listener
            binding.etAmount.removeTextChangedListener(currentWatcher)
            currentWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    entry.amount = s.toString().toDoubleOrNull() ?: 0.0
                    onAmountChanged()
                }
            }
            binding.etAmount.addTextChangedListener(currentWatcher)

            binding.etTitle.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    entry.title = s.toString()
                }
            })
        }
    }
}
