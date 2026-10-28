package dev.antonlammers.trainist.data.repository

import dev.antonlammers.trainist.data.local.dao.UserDataDao
import dev.antonlammers.trainist.domain.repository.UserDataRepository
import javax.inject.Inject

class UserDataRepositoryImpl @Inject constructor(
    private val dao: UserDataDao,
) : UserDataRepository {
    override suspend fun hasUserData(): Boolean = dao.hasUserData()
}
