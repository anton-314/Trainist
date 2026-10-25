package dev.antonlammers.trainist.fake

import dev.antonlammers.trainist.domain.backup.FoodEntryFile
import dev.antonlammers.trainist.domain.backup.FoodEntryFileReader

/** Returns [file] for every URI and records which ones were read. */
class FakeFoodEntryFileReader(var file: FoodEntryFile = FoodEntryFile.Unreadable) : FoodEntryFileReader {

    val readUris = mutableListOf<String>()

    override suspend fun read(uri: String): FoodEntryFile {
        readUris += uri
        return file
    }
}
