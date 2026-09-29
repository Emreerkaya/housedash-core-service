package com.housedash.adapters.inbound.http

import com.housedash.app.CaseIntake
import com.housedash.app.CreateCase
import com.housedash.app.CreateCaseFailure
import com.housedash.app.StoredCase
import com.housedash.app.SubmittedKey
import com.housedash.domain.case.CaseState
import com.housedash.domain.shared.Outcome
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

const val CASES_PATH = "/cases"

const val INTAKE_KEY_HEADER = "Idempotency-Key"

@RestController
class CaseController(
    private val createCase: CreateCase,
) {
    @PostMapping(CASES_PATH)
    fun create(
        @RequestHeader(name = INTAKE_KEY_HEADER, required = false) key: SubmittedKey?,
        @RequestBody(required = false) request: CreateCaseRequest?,
    ): ResponseEntity<CasesReply> =
        when (val outcome = createCase.handle(intakeOf(request, key))) {
            is Outcome.Ok -> answerFor(outcome.value)
            is Outcome.Err -> refusalFor(outcome.error)
        }
}

private fun intakeOf(
    request: CreateCaseRequest?,
    key: SubmittedKey?,
): CaseIntake? = request?.let { CaseIntake(it.nesterId, it.description, it.photoIds, key) }

private fun answerFor(stored: StoredCase): ResponseEntity<CasesReply> =
    ResponseEntity
        .status(if (stored.alreadyStored) HttpStatus.OK else HttpStatus.CREATED)
        .body(CreateCaseResponse(stored.id.value, CaseState.DESCRIBED.name))

private fun refusalFor(failure: CreateCaseFailure): ResponseEntity<CasesReply> =
    ResponseEntity.status(statusFor(failure)).body(CaseRefusal(reasonFor(failure)))
