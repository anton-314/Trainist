package dev.antonlammers.trainist.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "body_measurements", indices = [Index(value = ["date", "type"], unique = true)])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,
    val valueCm: Double,
)
