package com.gondev.kidstube.adapter

import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import kotlin.reflect.KClass

open class ArrayListRecyclerViewAdapter<VH: ViewBinder<ITEM>,ITEM: ViewType>(val itemList: ArrayList<ITEM>, @LayoutRes val layoutRes:Int, val vhClass: KClass<VH>): RecyclerView.Adapter<VH>() {

    constructor(vhClass: KClass<VH>,@LayoutRes layoutRes: Int) : this(ArrayList(),layoutRes,vhClass)

    override fun getItemCount(): Int {
        return itemList.size
    }

    override fun getItemViewType(position: Int): Int {
        return itemList[position].viewType();
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        //return ViewBinder(LayoutInflater.from(parent.context).inflate(R.layout.item_video_clip,parent,false),viewType)
        return vhClass.constructors.first().call(LayoutInflater.from(parent.context).inflate(this.layoutRes,parent,false),itemList)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(itemList[position],position)
    }

    fun add(item: ITEM) {
        insert(item, itemCount)
    }

    fun insert(item: ITEM, position: Int) {
        itemList.add(position, item)
        notifyItemInserted(position)
    }
}

class MultiViewRecyclerViewAdapter
(
        private val itemList: ArrayList<ViewType>,
        @LayoutRes
        private val layoutRes: IntArray,
        private vararg val viewBinders: KClass<out ViewBinder<out ViewType>>
): RecyclerView.Adapter<ViewBinder<ViewType>>()
{
    constructor(itemList: ArrayList<ViewType>,@LayoutRes viewType: Int,viewBinders: KClass<out ViewBinder<out ViewType>>)
     :this(itemList, intArrayOf(viewType),viewBinders)

    override fun getItemCount()=itemList.size

    override fun getItemViewType(position: Int) = itemList[position].viewType()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewBinder<ViewType> =
        viewBinders[viewType].constructors.first().call(LayoutInflater.from(parent.context).inflate(layoutRes[viewType],parent,false),itemList) as ViewBinder<ViewType>

    override fun onBindViewHolder(viewBinder: ViewBinder<ViewType>, position: Int) =
        viewBinder.bind(itemList[position],position)
}
