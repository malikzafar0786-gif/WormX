package com.wormx.app.downloads

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Audio extraction / format conversion for completed downloads.
 * Requires the `ffmpeg-kit-audio` (or `-full`) dependency — see build.gradle.kts.
 */
object MediaConverter {

    /** Extracts the audio track of [sourceVideo] into a new .mp3 next to it. Runs off the calling thread's caller — call from a background coroutine. */
    fun extractAudioToMp3(sourceVideo: File, onResult: (success: Boolean, output: File?) -> Unit) {
        val outputFile = File(sourceVideo.parentFile, sourceVideo.nameWithoutExtension + ".mp3")
        val command = "-i \"${sourceVideo.absolutePath}\" -vn -ar 44100 -ac 2 -b:a 192k \"${outputFile.absolutePath}\""

        FFmpegKit.executeAsync(command) { session ->
            val ok = ReturnCode.isSuccess(session.returnCode)
            onResult(ok, if (ok) outputFile else null)
        }
    }

    /** Re-encodes [sourceVideo] to a lower resolution/bitrate to save space before moving to Vault, etc. */
    fun compressVideo(sourceVideo: File, targetHeight: Int = 480, onResult: (success: Boolean, output: File?) -> Unit) {
        val outputFile = File(sourceVideo.parentFile, sourceVideo.nameWithoutExtension + "_${targetHeight}p.mp4")
        val command = "-i \"${sourceVideo.absolutePath}\" -vf scale=-2:$targetHeight -c:v libx264 -crf 26 -preset fast \"${outputFile.absolutePath}\""

        FFmpegKit.executeAsync(command) { session ->
            val ok = ReturnCode.isSuccess(session.returnCode)
            onResult(ok, if (ok) outputFile else null)
        }
    }
}
