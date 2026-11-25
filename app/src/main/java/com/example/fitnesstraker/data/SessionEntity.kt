package com.example.fitnesstraker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Tells Room this class describes a database table.
@Entity(tableName = "sessions")
//
//
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    //
    //

    val timestamp: Long, // when the session was saved
    val steps: Int, // sessionStepCount at the moment of saving
    val calories: Float,
    val distancekm: Float
)
