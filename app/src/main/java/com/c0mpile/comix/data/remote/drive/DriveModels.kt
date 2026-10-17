package com.c0mpile.comix.data.remote.drive

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Google Drive folder MIME type.
 */
const val MIME_TYPE_FOLDER: String = "application/vnd.google-apps.folder"

/**
 * Google Drive shortcut MIME type.
 */
const val MIME_TYPE_SHORTCUT: String = "application/vnd.google-apps.shortcut"

/**
 * Grouping of Google Drive REST v3 MIME type constants.
 */
object DriveMimeTypes {
    const val FOLDER: String = MIME_TYPE_FOLDER
    const val SHORTCUT: String = MIME_TYPE_SHORTCUT
}

/**
 * Target details for a Google Drive shortcut item (`application/vnd.google-apps.shortcut`).
 */
@Serializable
data class ShortcutDetails(
    val targetId: String? = null,
    val targetMimeType: String? = null
)

/**
 * Google Drive REST v3 file metadata representation.
 */
@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val md5Checksum: String? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val size: Long? = null,
    val modifiedTime: String? = null,
    val parents: List<String> = emptyList(),
    val shortcutDetails: ShortcutDetails? = null,
    val driveId: String? = null,
    val trashed: Boolean = false
)

/**
 * Paginated response from Google Drive `files.list`.
 */
@Serializable
data class DriveFileList(
    val files: List<DriveFile> = emptyList(),
    val nextPageToken: String? = null
)

/**
 * Single change item returned by Google Drive `changes.list`.
 */
@Serializable
data class DriveChange(
    val fileId: String,
    val removed: Boolean = false,
    val file: DriveFile? = null
)

/**
 * Paginated response from Google Drive `changes.list`.
 */
@Serializable
data class DriveChangeList(
    val changes: List<DriveChange> = emptyList(),
    val nextPageToken: String? = null,
    val newStartPageToken: String? = null
)

/**
 * Serializer for Google Drive's `size` field, which REST v3 returns as a JSON string
 * for 64-bit integers, while synthetic fixtures may provide either a JSON string or number.
 */
object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                return element.longOrNull ?: element.content.toLong()
            }
        }
        return decoder.decodeLong()
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
}
