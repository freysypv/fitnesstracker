package com.example.fitnesstraker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
// Dao data
// marks this interface as a set of database operations Room should imple
@Dao
interface SessionDao {

    // room writes the insert into session () SQL for us automatically
    // just from the shape of the SessionEntity
     @Insert
    fun insertSession(session: SessionEntity)

    // room
    //order
    @Query("Select * FROM sessions ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSessions(): List<SessionEntity>

}