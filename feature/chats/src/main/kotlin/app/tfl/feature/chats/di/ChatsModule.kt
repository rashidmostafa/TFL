package app.tfl.feature.chats.di

import app.tfl.feature.chats.AndroidChatClock
import app.tfl.feature.chats.ChatClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatsModule {
    @Binds
    abstract fun chatClock(impl: AndroidChatClock): ChatClock
}
