package dev.antonlammers.trainist.fake

import dev.antonlammers.trainist.domain.repository.UserDataRepository

class FakeUserDataRepository(var hasData: Boolean = false) : UserDataRepository {
    override suspend fun hasUserData(): Boolean = hasData
}
