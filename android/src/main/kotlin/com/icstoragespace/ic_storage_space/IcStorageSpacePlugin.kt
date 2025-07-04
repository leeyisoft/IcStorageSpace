package com.icstoragespace.ic_storage_space

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.util.UUID

/** IcStorageSpacePlugin */
class IcStorageSpacePlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "ic_storage_space")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")
            "getFreeDiskSpaceInBytes" -> result.success(getFreeDiskSpaceInBytes())
            "getTotalDiskSpaceInBytes" -> result.success(getTotalDiskSpaceInBytes())
            "storageStats" -> result.success(storageStats())
            "clearAllCache" -> result.success(clearAllCache())
            "homeDirectory" -> result.success(Environment.getExternalStorageDirectory().absolutePath)
            "deletePath" -> result.success(deletePath(call.argument("path")))
            "pathBytes" -> result.success(pathBytes(call.argument("path")))
            "pathList" -> result.success(pathList(call.argument("path")))
            else -> result.notImplemented()
        }
    }

    private fun getFreeDiskSpaceInBytes(): Long {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.blockSizeLong * stat.availableBlocksLong
        } else {
            stat.blockSize.toLong() * stat.availableBlocks.toLong()
        }
    }

    private fun getTotalDiskSpaceInBytes(): Long {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val storageVolumes = storageManager.storageVolumes
        var totalBytes = 0L
        for (volume in storageVolumes) {
            val uuid = volume.uuid?.let { UUID.fromString(it) } ?: StorageManager.UUID_DEFAULT
            totalBytes += storageStatsManager.getTotalBytes(uuid)
        }
        return totalBytes
    }

    private fun storageStats(): Map<String, Long?> {
        val packageName = context.packageName
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val storageVolumes = storageManager.storageVolumes
        val user = Process.myUserHandle()
        var appBytes = 0L
        var cacheBytes = 0L
        var dataBytes = 0L

        for (volume in storageVolumes) {
            if ("mounted" == volume.state && volume.isPrimary) {
                val uuid = volume.uuid?.let { UUID.fromString(it) } ?: StorageManager.UUID_DEFAULT
                val stats = storageStatsManager.queryStatsForPackage(uuid, packageName, user)
                appBytes += stats.appBytes
                cacheBytes += stats.cacheBytes
                dataBytes += stats.dataBytes
            }
        }

        return mapOf(
            "cacheBytes" to cacheBytes,
            "dataBytes" to dataBytes,
            "appBytes" to appBytes
        )
    }

    private fun pathList(path: String?): List<String> {
        return if (path.isNullOrEmpty()) {
            getAllFilesAndDirectories(context.filesDir)
        } else {
            getAllFilesAndDirectories(File(path))
        }
    }

    private fun getAllFilesAndDirectories(directory: File): List<String> {
        val filePaths = mutableListOf<String>()
        if (directory.isDirectory) {
            val files = directory.listFiles()
            files?.forEach { file ->
                if (file.isFile) {
                    filePaths.add(file.absolutePath)
                } else if (file.isDirectory) {
                    filePaths.addAll(getAllFilesAndDirectories(file))
                }
            }
        }
        return filePaths
    }

    private fun pathBytes(path: String?): Long? {
        return if (path.isNullOrEmpty()) null else fileBytes(File(path))
    }

    private fun fileBytes(file: File): Long {
        return if (file.isFile) {
            file.length()
        } else {
            var size = 0L
            file.listFiles()?.forEach {
                size += if (it.isFile) it.length() else fileBytes(it)
            }
            size
        }
    }

    private fun clearAllCache(): Boolean {
        return try {
            deleteFiles(context.cacheDir)
            deleteFiles(context.codeCacheDir)
            context.externalCacheDir?.let { deleteFiles(it) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun deletePath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return deleteFiles(File(path))
    }

    private fun deleteFiles(dir: File): Boolean {
        try {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { deleteFiles(it) }
            }
            dir.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}
