package com.onlion.androidqcompatibilitytest

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.guoxiaoxing.phoenix.compress.video.engine.MediaTranscoderEngine
import com.onlion.androidqsupport.AndroidQFileRepo
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.security.Permissions


class MainActivity : AppCompatActivity() {
companion object{
    const val TAG ="MainActivity"
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE),100)
        button.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                //创建了一个红色的图片
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.RED)
                val uri = AndroidQFileRepo.saveImageToExternal(
                    applicationContext,
                    "androidqtest",
                    "test.jpg",
                    bitmap
                )
                imageView.setImageURI(uri)
//                 AndroidQFileRepo.getExternalImages(
//                    applicationContext,
//                    "${MediaStore.Images.Media.DISPLAY_NAME}=?",
//                    arrayOf("test.jpg")
//                ).apply {
//                     Log.d("tet",this.toString())
//                 }
//                     .forEach {
//                     textView.text = "${textView.text}\n${it.uri}"
//                 }
            }

        }

    }
}
