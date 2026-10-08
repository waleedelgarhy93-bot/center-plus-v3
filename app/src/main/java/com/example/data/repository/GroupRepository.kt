package com.example.data.repository

import com.example.data.dao.GroupDao
import com.example.data.model.Group
import com.example.data.model.GroupWithSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class GroupRepository(private val groupDao: GroupDao) {

    fun getAllGroups(): Flow<List<Group>> = groupDao.getAllGroups()

    fun getAllGroupsWithSubject(): Flow<List<GroupWithSubject>> =
        groupDao.getAllGroupsWithSubject()

    suspend fun getGroupById(id: Long): Group? = withContext(Dispatchers.IO) {
        groupDao.getGroupById(id)
    }

    suspend fun insertGroup(group: Group): Long = withContext(Dispatchers.IO) {
        groupDao.insertGroup(group)
    }

    suspend fun updateGroup(group: Group) = withContext(Dispatchers.IO) {
        groupDao.updateGroup(group)
    }

    suspend fun deleteGroup(group: Group) = withContext(Dispatchers.IO) {
        groupDao.deleteGroup(group)
    }

    suspend fun deleteGroupById(id: Long) = withContext(Dispatchers.IO) {
        groupDao.deleteGroupById(id)
    }
}
