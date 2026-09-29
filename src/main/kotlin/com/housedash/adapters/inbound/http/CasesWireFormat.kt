package com.housedash.adapters.inbound.http

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.housedash.app.SubmittedId
import com.housedash.app.SubmittedText

class CreateCaseRequest
    @JsonCreator
    constructor(
        @JsonProperty("nesterId") val nesterId: SubmittedId?,
        @JsonProperty("description") val description: SubmittedText?,
        @JsonProperty("photoIds") val photoIds: List<SubmittedId>?,
    )

sealed interface CasesReply

class CreateCaseResponse(
    val caseId: String,
    val state: String,
) : CasesReply

class CaseRefusal(
    val reason: String,
) : CasesReply
