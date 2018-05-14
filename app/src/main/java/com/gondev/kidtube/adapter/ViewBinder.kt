package com.gondev.kidstube.adapter

import android.support.v7.widget.RecyclerView
import android.view.View

abstract class ViewBinder<ITEM : ViewType>(itemView: View, val itemList: ArrayList<ITEM>) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

    internal abstract fun bind(item: ITEM, position: Int)

    override fun onClick(view: View?) {
    }
}

/**
 * item의 viewType을 정의 하기 위해서 상속 받아야 하는 클레스
 * 기본 viewType은 0
 */
interface ViewType {
    fun viewType(): Int
}