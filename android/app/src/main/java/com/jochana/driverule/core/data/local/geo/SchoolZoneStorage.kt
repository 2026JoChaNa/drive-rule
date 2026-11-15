package com.jochana.driverule.core.data.local.geo

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** One envelope contains both the verified data bytes and their manifest/ETag. */
internal interface SchoolZoneStorage {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

internal const val MAX_SCHOOL_ZONE_STORAGE_BYTES = 3 * 1_024 * 1_024

internal class AtomicSchoolZoneStorage(context: Context) : SchoolZoneStorage {
    private val file = File(context.applicationContext.noBackupFilesDir, "geo/school-zones-v1.json")
    private val atomic = AtomicFile(file)

    override fun read(): ByteArray? {
        if (!file.exists() && !File(file.path + ".bak").exists() && !File(file.path + ".new").exists()) return null
        return atomic.openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (output.size() <= MAX_SCHOOL_ZONE_STORAGE_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_SCHOOL_ZONE_STORAGE_BYTES + 1 - output.size()))
                if (count < 0) break
                if (count > 0) output.write(buffer, 0, count)
            }
            if (output.size() > MAX_SCHOOL_ZONE_STORAGE_BYTES) throw IOException("School zone cache exceeds capacity")
            output.toByteArray()
        }
    }

    override fun write(bytes: ByteArray) {
        require(bytes.size <= MAX_SCHOOL_ZONE_STORAGE_BYTES)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            stream.flush()
            stream.fd.sync()
            atomic.finishWrite(stream)
            stream = null
            if (read()?.contentEquals(bytes) != true) throw IOException("School zone commit not verified")
        } catch (failure: Exception) {
            stream?.let { atomic.failWrite(it) }
            throw failure
        }
    }
}
