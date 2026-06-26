package com.lorenzo.aicalendar.domain.model

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** How an event repeats. [interval] = every N units (e.g. WEEKLY interval 2 = every 2 weeks). */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int = 1,
)
