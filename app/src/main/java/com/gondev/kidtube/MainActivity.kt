package com.gondev.kidtube

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.Dialog
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.View
import com.bumptech.glide.Glide
import com.gondev.kidstube.adapter.MultiViewRecyclerViewAdapter
import com.gondev.kidstube.adapter.ViewBinder
import com.gondev.kidstube.adapter.ViewType
import com.gondev.kidtube.exoplayer.startVideoActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_video_clip.view.*
import java.io.File
import java.lang.Exception

val REQUEST_EXTERNAL_STORAGE_PERMISSION: Int = 120
val FRAGMENT_DIALOG: String = "dialog"

class MainActivity: AppCompatActivity() {

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val decorView = window.decorView
        var uiOption = decorView.systemUiVisibility

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            uiOption = uiOption or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            uiOption = uiOption or View.SYSTEM_UI_FLAG_FULLSCREEN

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            uiOption = uiOption or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        decorView.setSystemUiVisibility(uiOption)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestReadExternalStoragePermission()
            return
        }

        init()
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun requestReadExternalStoragePermission()
    {

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            ConfirmationDialog().show(supportFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_EXTERNAL_STORAGE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_EXTERNAL_STORAGE_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(supportFragmentManager, FRAGMENT_DIALOG)

                return
            }

            File(Environment.getExternalStorageDirectory(),"kidtube").mkdirs()
            init()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    fun init()
    {
        recyclerview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,false)
        //recyclerview.adapter= ArrayListRecyclerViewAdapter(getAllMedia(), R.layout.item_video_clip, VideoClipViewHolder::class)
        recyclerview.adapter= MultiViewRecyclerViewAdapter(getAllMedia(), R.layout.item_video_clip, VideoClipViewHolder::class)
    }

    fun getAllMedia(): ArrayList<ViewType> {
        val projection = arrayOf(MediaStore.Video.VideoColumns.DATA, MediaStore.Video.Media.DISPLAY_NAME)
        val cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Video.VideoColumns.DATA} LIKE ?", arrayOf("%kidtube%"), null)
        //val cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)
        Log.d("tset",cursor.count.toString())
        val videoClipList=ArrayList<ViewType>()
        try {
            while (cursor.moveToNext())
            {
                videoClipList.add(VideoClipData(cursor))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        finally {
            cursor.close()
        }
        return videoClipList
    }
}

class ConfirmationDialog() : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val parent = activity
        return AlertDialog.Builder(activity)
                .setMessage(R.string.request_permission)
                .setPositiveButton(android.R.string.ok,
                        { dialog, which ->
                            ActivityCompat.requestPermissions(activity!!,arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                    REQUEST_EXTERNAL_STORAGE_PERMISSION)
                        })
                .setNegativeButton(android.R.string.cancel,
                        { dialog, which ->
                            if (activity != null) {
                                Log.e("test","app finished")
                                activity!!.finish()
                            }
                        })
                .create()
    }
}

/**
 * Shows an error message dialog.
 */
class ErrorDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = activity
        return AlertDialog.Builder(activity)
                .setMessage(arguments!!.getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { dialogInterface, i ->
                    activity!!.finish()
                }
                .create()
    }

    companion object {

        private val ARG_MESSAGE = "message"

        fun newInstance(message: String): ErrorDialog {
            val dialog = ErrorDialog()
            val args = Bundle()
            args.putString(ARG_MESSAGE, message)
            dialog.arguments = args
            return dialog
        }
    }
}

class VideoClipViewHolder(itemView: View, itemList: ArrayList<VideoClipData>): ViewBinder<VideoClipData>(itemView, itemList)
{
    override fun bind(item: VideoClipData, position: Int) {
        Glide.with(itemView.context)
                .asBitmap()
                .load(File(item.videoPath))
                .into(itemView.imgThumbnail)

        /*val thumbnail=ThumbnailUtils.createVideoThumbnail(item.videoPath,MediaStore.Images.Thumbnails.MINI_KIND)
        itemView.imgThumbnail.setImageBitmap(thumbnail)*/

        itemView.txtTitle.setText(item.title)

        itemView.setOnClickListener {v->
            v.context.startVideoActivity(itemList,position)
        }
    }
}

data class VideoClipData(val videoPath: String,val title: String): ViewType, Parcelable {
    constructor(cursor: Cursor) : this(cursor.getString(cursor.getColumnIndex(MediaStore.Video.VideoColumns.DATA))
            .apply {  Log.d("insert video",this) },
            cursor.getString(cursor.getColumnIndex(MediaStore.Video.VideoColumns.DISPLAY_NAME)))

    override fun viewType()=0

    val videoUri by lazy { Uri.fromFile(File(videoPath))}

    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.run {
            writeString(videoPath)
            writeString(title)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<VideoClipData> {
        override fun createFromParcel(parcel: Parcel): VideoClipData {
            return VideoClipData(parcel)
        }

        override fun newArray(size: Int): Array<VideoClipData?> {
            return arrayOfNulls(size)
        }
    }
}
