package com.jochana.driverule.domain.guidance

/** Session-local duplicate history; this is not a persisted trip or driver history. */
class GuidanceEventPolicy {
    private val recentIds = LinkedHashSet<String>()

    fun accept(event: GuidanceEvent, nowElapsedMs: Long): Boolean {
        if (event.expiresAtElapsedMs <= nowElapsedMs || event.id in recentIds) return false
        recentIds.add(event.id)
        if (recentIds.size > MAX_REMEMBERED_IDS) {
            val oldest = recentIds.iterator()
            oldest.next()
            oldest.remove()
        }
        return true
    }

    fun reset() = recentIds.clear()

    companion object {
        const val MAX_REMEMBERED_IDS = 64
    }
}
