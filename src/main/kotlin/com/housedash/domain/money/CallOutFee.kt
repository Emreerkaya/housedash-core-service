package com.housedash.domain.money

enum class VisitEnd {
    NESTER_DECLINED_REVISION,
    NESTER_CANCELLED,
    TASKER_CANCELLED,
    TASKER_NO_SHOW,
}

data class VisitEnding(
    val taskerArrived: Boolean,
    val endedBy: VisitEnd,
)

object CallOutFee {
    const val PERCENT_OF_THE_ACCEPTED_TOTAL = 20L

    const val CAP_CENTS = 2500L

    val CAP = Money(CAP_CENTS)

    private const val WHOLE = 100L

    private val SMALLEST_TOTAL_THE_CAP_BINDS_AT = Money(CAP_CENTS * WHOLE / PERCENT_OF_THE_ACCEPTED_TOTAL)

    fun payableOn(ending: VisitEnding): Boolean {
        val revisionDeclined = ending.endedBy == VisitEnd.NESTER_DECLINED_REVISION
        return ending.taskerArrived && revisionDeclined
    }

    fun of(acceptedTotal: Money): Money =
        if (acceptedTotal >= SMALLEST_TOTAL_THE_CAP_BINDS_AT) {
            CAP
        } else {
            acceptedTotal.percentRoundedHalfUp(PERCENT_OF_THE_ACCEPTED_TOTAL)
        }
}
