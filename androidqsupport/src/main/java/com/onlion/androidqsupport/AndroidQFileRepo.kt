package com.onlion.androidqsupport

import android.annotation.TargetApi
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

object AndroidQFileRepo {
    private const val TAG = "AndroidQFileRepo"


    private fun isCurrentSDKVersionBelowQ(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    @Suppress("DEPRECATION")
    val DCIM =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            .toString()

    /* Checks if external storage is available for read and write */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /* Checks if external storage is available to at least read */
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }


    fun writeFile(path: String, data: ByteArray) {
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(path)
            out.write(data)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Failed to write data", e)
        } finally {
            try {
                out!!.close()
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "Failed to close file after write", e)
            }
        }
    }

    /**
     * [relativePath] eg:"testImage" 经测试目前只支持一级目录，多层目录实测只截取最后一层目录
     */
    suspend fun saveImageToExternal(
        context: Context,
        relativePath: String,
        fileName: String,
        image: Bitmap
    ) = withContext(Dispatchers.IO) {
        var uri: Uri? = null
        val contentValues = getMediaContentValues(relativePath, fileName, true)

//        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        try {
            val queryImage = queryImage(context, name = fileName)
            uri = queryImage ?: context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )?.also {
                //                if (!isCurrentSDKVersionBelowQ()){
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    image.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    outputStream.flush()
                    outputStream.close()
                }
            }

        } catch (e: Exception) {
            // This can happen when the external volume is already mounted, but
            // MediaScanner has not notify MediaProvider to add that volume.
            // The picture is still safe and MediaScanner will find it and
            // insert it into MediaProvider. The only problem is that the user
            // cannot click the thumbnail to review the picture.
            Log.e(TAG, "Failed to write MediaStore$e")
        }
        uri
    }

    /**
     * androidQ 保存视频到外部空间，同时兼容低版本
     */
    suspend fun saveVideoToExternal(
        context: Context,
        relativePath: String,
        fileName: String,
        videoByte: ByteArray
    ) = withContext(Dispatchers.IO) {
        var uri: Uri? = null
        val contentValues = getMediaContentValues(relativePath, fileName, false)
//        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        try {
            val queryVideo = queryVideo(context, fileName)
            uri = queryVideo ?: context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )?.also {
                val outputStream = context.contentResolver.openOutputStream(it)
                val inputStream = ByteArrayInputStream(videoByte)
                if (outputStream != null) {
                    copy(inputStream, outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write video to MediaStore$e")
        }
        uri

    }

    private fun getMediaContentValues(
        relativePath: String,
        fileName: String,
        isImage: Boolean
    ): ContentValues {
        return ContentValues().apply {
            if (isImage) {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (!isCurrentSDKVersionBelowQ()) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$relativePath"
                    )
                } else {
                    val file =
                        File(DCIM + File.separator + relativePath + File.separator + fileName)
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                }
            } else {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (!isCurrentSDKVersionBelowQ()) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$relativePath"
                    )
                } else {
                    val file =
                        File(DCIM + File.separator + relativePath + File.separator + fileName)
                    if (!file.exists()) {
                        file.createNewFile()
                    }
                    put(MediaStore.Video.Media.DATA, file.absolutePath)
                }
            }
        }
    }


    /**
     * 在沙盒创建指定文件夹,此方式创建的文件夹会随着应用的卸载而被移除，如果需要卸载之后继续保持请使用mediastore
     */
    fun getPrivateAlbumStorageDir(context: Context, albumName: String): File? {
        // Get the directory for the app's private pictures directory.
        val file = File(
            context.getExternalFilesDir(
                Environment.DIRECTORY_PICTURES
            ), albumName
        )
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created")
        }
        return file
    }

    /**
     * 将沙盒图片保存至外部存储空间
     * [newPath] is Environment.DIRECTORY_PICTURES + File.separator + test);
     */
    suspend fun saveSandboxImageToExternal(file: File, context: Context, newPath: String) =
        withContext(Dispatchers.IO) {
            val contentValues = getMediaContentValues(newPath, file.name, true)
//        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            saveSandBoxMediaFileToExternal(context, file, uri)

        }

    /**
     * 将沙盒视频保存至外部存储空间
     * [newPath] is Environment.DIRECTORY_MOVIES + File.separator + test);
     */
    suspend fun saveSandboxVideoToExternal(file: File, context: Context, newPath: String) =
        withContext(Dispatchers.IO) {
            val contentValues = getMediaContentValues(newPath, file.name, false)
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            saveSandBoxMediaFileToExternal(context, file, uri)
//        contentValues.clear()
//        contentValues.put(MediaStore.Images.Media.IS_PENDING,0)
//        uri?.let { context.contentResolver.update(it,contentValues,null,null) }
        }


    /**
     * 将沙盒媒体文件保存至外部存储空间
     */
    private fun saveSandBoxMediaFileToExternal(context: Context, file: File, uri: Uri?): Boolean {
        if (uri == null) {
            return false
        }
        val contentResolver = context.contentResolver
        contentResolver.openFileDescriptor(uri, "w")?.also {
            val fileOutputStream = FileOutputStream(it.fileDescriptor)
            val fileInputStream = FileInputStream(file)
            return try {
                copy(fileInputStream, fileOutputStream)
                true
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    @Throws(IOException::class)
    private fun copy(ist: InputStream, ost: OutputStream) {
        val buffer = ByteArray(4096)
        var byteCount: Int
        while (ist.read(buffer).also { byteCount = it } != -1) {
            ost.write(buffer, 0, byteCount)
        }
        ost.flush()
    }


    /**
     *  获取文件的uri
     * eg:
     * val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
     * val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
     * val args = arrayOf("Image.png")
     * val projection = arrayOf(MediaStore.Images.Media._ID)
     */
    suspend fun queryUri(
        context: Context,
        externalUri: Uri,
        selection: String,
        args: Array<String>,
        projection: Array<String>
    ): Uri? = withContext(Dispatchers.IO) {
        var queryUri: Uri? = null

        val cursor = context.contentResolver.query(externalUri, projection, selection, args, null)
        if (cursor != null && cursor.moveToFirst()) {
            queryUri = ContentUris.withAppendedId(externalUri, cursor.getLong(0))
            Log.d(TAG, "查询成功，Uri路径$queryUri")
            cursor.close()
        }
        queryUri
    }


    /**
     * androidQ 获取所有的图片
     * eg:
     *  val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
     *  val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
     *  val args = arrayOf("Image.png")
     *  val projection = arrayOf(MediaStore.Images.Media._ID)
     *  val scopedSortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
     */
    suspend fun getExternalImages(
        context: Context,
        selection: String,
        args: Array<String>,
        scopedSortOrder: String? = null
    ): List<Image> = withContext(Dispatchers.IO) {
        val queriedImages = arrayListOf<Image>()
//        val scopedSortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val externalUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = if (isCurrentSDKVersionBelowQ()) {
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media._ID
            )
        } else {
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media._ID
            )
        }
        val cursor =
            context.contentResolver.query(externalUri, projection, selection, args, scopedSortOrder)


        cursor.use {
            it?.let {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn =
                    if (!isCurrentSDKVersionBelowQ())
                        it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                    else -1

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val size = it.getString(sizeColumn)
                    val date = if (!isCurrentSDKVersionBelowQ()) it.getString(dateColumn) else null

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
//                    val thumbnail =
//                        context.contentResolver.loadThumbnail(contentUri, Size(480, 480), null)
                    queriedImages.add(Image(contentUri, name, size, date))

                }
            } ?: kotlin.run {
                Log.e("TAG", "Cursor is null!")
            }
        }
        queriedImages
    }


    /**
     * 通过图片名称获取图片uri
     */
    suspend fun queryImage(context: Context, name: String): Uri? {
        val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=?"
        val args = arrayOf(name)
        val projection = arrayOf(MediaStore.Images.Media._ID)
        return queryUri(context, external, selection, args, projection)
    }

    /**
     * 通过视频名称获取图片uri
     */
    suspend fun queryVideo(context: Context, name: String): Uri? {
        val external = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME}=?"
        val args = arrayOf(name)
        val projection = arrayOf(MediaStore.Video.Media._ID)
        return queryUri(context, external, selection, args, projection)
    }


    /**
     * 通过[uri]获取缩略图
     */
    @TargetApi(Build.VERSION_CODES.Q)
    private fun getThumbnails(uri: Uri, context: Context, size: Size = Size(480, 480)): Bitmap {
        return context.contentResolver.loadThumbnail(uri, size, null)
    }
}