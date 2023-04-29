package com.example.encryptedmessenger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MyAdapter(private val listener: OnItemClickListener, private val dataClass: ArrayList<DataClass>): RecyclerView.Adapter<MyAdapter.ViewHolderClass>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderClass {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_layout,  parent, false)
        return ViewHolderClass(itemView)

    }

    override fun getItemCount(): Int {
        return dataClass.size

    }

    override fun onBindViewHolder(holder: ViewHolderClass, position: Int) {
        val currentItem = dataClass[position]
        holder.rvUsername.text = currentItem.dataName

        }


    inner class ViewHolderClass(itemView: View): RecyclerView.ViewHolder(itemView),
    View.OnClickListener{
        val rvUsername: TextView = itemView.findViewById<TextView>(R.id.User)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if(position != RecyclerView.NO_POSITION){
                listener.onItemClick(position)
            }
        }
    }
    interface OnItemClickListener{
        fun onItemClick(position: Int)
    }
}
