package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Group
import com.example.data.model.GroupWithSubject
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM `groups` ORDER BY id DESC")
    fun getAllGroups(): Flow<List<Group>>

    @Query(
        """
        SELECT 
            g.id AS id,
            g.subjectId AS subjectId,
            s.name AS subjectName,
            s.color AS subjectColor,
            s.icon AS subjectIcon,
            g.grade AS grade,
            g.center AS center,
            g.days AS days,
            g.time AS time,
            g.price AS price,
            g.notes AS notes,
            (SELECT COUNT(*) FROM students st WHERE st.groupName = g.grade || ' - ' || g.center OR st.groupName LIKE '%' || g.center || '%') AS studentCount
        FROM `groups` g
        INNER JOIN subjects s ON g.subjectId = s.id
        ORDER BY g.id DESC
        """
    )
    fun getAllGroupsWithSubject(): Flow<List<GroupWithSubject>>

    @Query("SELECT * FROM `groups` WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: Long): Group?

    @Query("SELECT COUNT(*) FROM `groups` WHERE subjectId = :subjectId")
    suspend fun getGroupCountBySubject(subjectId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroups(groups: List<Group>)

    @Update
    suspend fun updateGroup(group: Group)

    @Delete
    suspend fun deleteGroup(group: Group)

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    @Query("SELECT COUNT(*) FROM `groups`")
    suspend fun getCount(): Int
}
