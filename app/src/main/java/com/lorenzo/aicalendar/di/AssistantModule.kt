package com.lorenzo.aicalendar.di

import com.lorenzo.aicalendar.data.assistant.HybridAssistant
import com.lorenzo.aicalendar.data.chat.RoomChatRepository
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.chat.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: RoomChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun bindAiAssistant(impl: HybridAssistant): AiAssistant
}
