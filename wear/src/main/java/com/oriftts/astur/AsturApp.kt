package com.oriftts.astur

import android.app.Application
import com.oriftts.astur.data.ChatHistoryRepository
import com.oriftts.astur.data.SettingsRepository
import com.oriftts.astur.data.StatsRepository

/**
 * 全局 Application：持有唯一的仓库实例，
 * 避免重复创建 DataStore 导致进程内冲突。
 */
class AsturApp : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val statsRepository: StatsRepository by lazy { StatsRepository(this) }
    val chatHistoryRepository: ChatHistoryRepository by lazy { ChatHistoryRepository(this) }
}
