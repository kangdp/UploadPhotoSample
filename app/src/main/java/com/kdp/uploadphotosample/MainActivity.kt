package com.kdp.uploadphotosample

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProvider
import androidx.appcompat.app.AppCompatActivity

import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.common.lib.permission.KPermission
import kotlinx.android.synthetic.main.activity_main.*

import java.io.File
import java.lang.IndexOutOfBoundsException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.log

/**
 * 拍照、相册
 */
class MainActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val REQUEST_IMAGE_CAPTURE = 0 //拍照
        private const val REQUEST_OPEN_ALBUM = 1    //相册
        private const val REQUEST_IMAGE_CROP = 2  //裁剪
    }


    private var filePath: String? = null
    private var fileUri: Uri? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnOpenCamera.setOnClickListener(this)
        btnOpenAlbum.setOnClickListener(this)
    }

    private fun requestPermission() {
        KPermission(this).request(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .execute { permission ->
                    if (permission.isGrant){
                        //权限申请成功
                        openCamera()
                    }else{
                        Toast.makeText(this@MainActivity,"sdcard 权限被拒绝!",Toast.LENGTH_SHORT).show()
                    }
                }
    }

    override fun onClick(v: View) {
        if (v === btnOpenCamera) {
            requestPermission()
        } else if (v === btnOpenAlbum) {
            openAblum()
        }
    }

    private fun openAblum() {
        val intent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_OPEN_ALBUM)
    }

    /**
     * 打开相机
     */
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile = createFile()
            filePath = photoFile.absolutePath
            fileUri = createFileUri(photoFile)
            intent.putExtra(MediaStore.EXTRA_OUTPUT,fileUri)
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        }
    }

    private fun createFileUri(file: File): Uri? {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Uri.fromFile(file)
        } else {
            FileProvider.getUriForFile(this,BuildConfig.APPLICATION_ID,file)
        }
    }

    private fun createFile(): File {
        return Environment.getExternalStoragePublicDirectory("${Environment.DIRECTORY_PICTURES}/${System.currentTimeMillis()}.png")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            Log.d(MainActivity::class.java.simpleName,"file path = $filePath")
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    clipPhoto(fileUri)
                }
                REQUEST_OPEN_ALBUM -> {
                    openAlbumCallback(data)

                }
                REQUEST_IMAGE_CROP -> {
                    takePhotoCallback()
                }
            }
        }
    }

    private fun openAlbumCallback(data: Intent?) {
        Executors.newSingleThreadExecutor().execute{
            val  selectedImage = data?.data
            val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
            val cursor: Cursor? = contentResolver.query(selectedImage,filePathColumn,null,null,null)
            cursor?.moveToFirst()
            val pictureUrl = cursor?.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
            filePath = pictureUrl
            val file = File(filePath)
            if (file.exists()){
                Log.d(MainActivity::class.java.simpleName,"exits")
            }
            fileUri = createFileUri(file)
            runOnUiThread {
                clipPhoto(fileUri)
            }
        }
    }

    private fun takePhotoCallback() {
        Executors.newSingleThreadExecutor().execute {
            //需要手动刷新MediaStore
            MediaScannerConnection.scanFile(this
                    , arrayOf(filePath)
                    , arrayOf("image/png")) { path, _ ->
                //更新回调
                Log.d(MainActivity::class.java.simpleName,"scan completed = $path" )
                val cursor:Cursor? = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,null,MediaStore.Images.Media.DATA + " = ?", arrayOf(filePath),null)
                while (cursor?.moveToNext() == true){
                    val imgUrl = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
                    Log.d(MainActivity::class.java.simpleName,"imgUrl=$imgUrl")
                    runOnUiThread {
                        ivImg.setImageBitmap(BitmapFactory.decodeFile(imgUrl))
                    }
                }
            }
        }
    }

    /**
     * 裁剪图片
     */
    private fun clipPhoto(uri: Uri?){
        val intent = Intent("com.android.camera.action.CROP")
        intent.setDataAndType(uri,"image/*")
        intent.putExtra("crop",true)
        //裁剪的宽高比
        intent.putExtra("aspectX", 1)
        intent.putExtra("aspectY", 1)
        //裁剪后图片的宽高
        intent.putExtra("outputX", ivImg?.measuredWidth)
        intent.putExtra("outputY", ivImg?.measuredHeight)
        intent.putExtra("return-data", false)//表示data中返回null
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)//设置裁剪后图片保存的路径
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            //7.0以上需要加上两个权限，否则获取不到裁剪后的图片
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        //设置裁剪后图片的格式
        intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString())
        //将图片拉伸，否贼图片周围会出现黑边
        intent.putExtra("scale",true)
        intent.putExtra("scaleUpIfNeeded", true)
        intent.putExtra("noFaceDetection", false)
        startActivityForResult(intent,REQUEST_IMAGE_CROP)


    }


}
