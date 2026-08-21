package com.wormx.app.downloads

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Audio extraction for completed video downloads, built entirely on Android's
 * own MediaExtractor/MediaMuxer APIs — no external media library required.
 *
 * Note: this remuxes the video's existing audio track (almost always AAC)
 * into a standalone `.m4a` file rather than transcoding to `.mp3` — Android
 * has no built-in MP3 *encoder*, only a decoder, so true MP3 output would
 * need a third-party codec library. M4A/AAC plays natively everywhere
 * (including directly in WormX's own player) and this path needs nothing
 * beyond the Android SDK, so it can't go stale the way an external
 * dependency can.
 */
object MediaConverter {

    /**
     * Extracts the audio track of [sourceVideo] into a new `.m4a` file next to it.
     * Safe to call from a background thread/coroutine; reports back on the same thread.
     */
    fun extractAudio(sourceVideo: File, onResult: (success: Boolean, output: File?) -> Unit) {
        val outputFile = File(sourceVideo.parentFile, sourceVideo.nameWithoutExtension + ".m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(sourceVideo.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                onResult(false, null)
                return
            }

            extractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(outTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            onResult(true, outputFile)
        } catch (e: Exception) {
            outputFile.delete()
            onResult(false, null)
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }
}
