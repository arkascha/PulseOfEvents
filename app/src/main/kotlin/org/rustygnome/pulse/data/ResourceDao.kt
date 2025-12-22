package org.rustygnome.pulse.data

import androidx.room.*

@Dao
interface ResourceDao {
    @Query("SELECT * FROM resources ORDER BY position ASC")
    fun getAll(): List<Resource>

    @Query("SELECT * FROM resources WHERE id = :id")
    fun getById(id: Long): Resource?

    @Insert
    fun insert(resource: Resource): Long

    @Update
    fun update(resource: Resource)

    @Delete
    fun delete(resource: Resource)

    @Transaction
    fun updatePositions(resources: List<Resource>) {
        resources.forEach { update(it) }
    }
}
