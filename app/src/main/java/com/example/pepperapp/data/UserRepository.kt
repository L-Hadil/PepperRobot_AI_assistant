package com.example.pepperrobot_ai_assistant.data

import com.example.pepperapp.data.UserProfileDao
import com.example.pepperapp.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(private val userProfileDao: UserProfileDao) {

    suspend fun insertProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        userProfileDao.insert(profile)
    }

    suspend fun getProfileById(id: String): UserProfile? = withContext(Dispatchers.IO) {
        userProfileDao.getById(id)
    }

    suspend fun getAllProfiles(): List<UserProfile> = withContext(Dispatchers.IO) {
        userProfileDao.getAll()
    }

    suspend fun deleteProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        userProfileDao.delete(profile)
    }

    suspend fun clearAllProfiles() = withContext(Dispatchers.IO) {
        userProfileDao.clearAll()
    }
}
