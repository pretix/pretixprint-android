package eu.pretix.pretixprint.ui

import androidx.databinding.ViewDataBinding

class BindingHolder<B : ViewDataBinding>(public val binding: B) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)
