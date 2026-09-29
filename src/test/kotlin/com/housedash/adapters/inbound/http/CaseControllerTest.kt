package com.housedash.adapters.inbound.http

import com.housedash.adapters.outbound.persistence.InMemoryCaseRepository
import com.housedash.app.CreateCase
import com.housedash.app.FIXED_CLOCK
import com.housedash.app.PHONE_IN_A_DESCRIPTION
import com.housedash.app.RandomCaseIdentifiers
import com.housedash.app.RefusingCaseIdentifiers
import com.housedash.app.TAP_DESCRIPTION
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.assertEquals

private const val AN_INTAKE_KEY = "intake-1"

private fun bodyOf(
    owner: String = "\"ns_1\"",
    description: String = "\"$TAP_DESCRIPTION\"",
    photoIds: String = """["ph_1","ph_2"]""",
): String = """{"nesterId":$owner,"description":$description,"photoIds":$photoIds}"""

class CaseControllerTest {
    private val cases = InMemoryCaseRepository()

    private val mockMvc: MockMvc = mockMvcFor(RandomCaseIdentifiers())

    private fun mockMvcFor(identifiers: com.housedash.app.CaseIdentifiers): MockMvc =
        MockMvcBuilders
            .standaloneSetup(CaseController(CreateCase(cases, identifiers, FIXED_CLOCK)))
            .build()

    @Test
    fun `a described intake answers created with the case identifier and the state`() {
        mockMvc
            .perform(
                post(CASES_PATH)
                    .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyOf()),
            ).andExpect(status().isCreated)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.state").value("DESCRIBED"))
            .andExpect(jsonPath("$.caseId").exists())
        assertEquals(1, cases.storedCases())
    }

    @Test
    fun `the same intake key answers ok with the same case identifier and stores one case`() {
        val first = created()
        val second =
            mockMvc
                .perform(
                    post(CASES_PATH)
                        .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf()),
                ).andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        assertEquals(first, second)
        assertEquals(1, cases.storedCases())
    }

    @Test
    fun `a description carrying a phone number answers unprocessable content, not a server error`() {
        mockMvc
            .perform(
                post(CASES_PATH)
                    .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyOf(description = "\"$PHONE_IN_A_DESCRIPTION\"")),
            ).andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.reason").value("ContactDetailsInDescription"))
        assertEquals(0, cases.storedCases())
    }

    @Test
    fun `a refusal body carries the reason and never the text that was refused`() {
        val refusal =
            mockMvc
                .perform(
                    post(CASES_PATH)
                        .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(description = "\"$PHONE_IN_A_DESCRIPTION\"")),
                ).andReturn()
                .response
                .contentAsString
        assertEquals("""{"reason":"ContactDetailsInDescription"}""", refusal)
    }

    @Test
    fun `each shape the domain refuses answers bad request with its own reason`() {
        refusedWith(bodyOf(description = "\"tap drips\""), "DescriptionTooShort")
        refusedWith(bodyOf(photoIds = """["ph_1","ph_1"]"""), "DuplicatePhoto")
        refusedWith(bodyOf(photoIds = """["ph_1","ph_2","ph_3","ph_4","ph_5"]"""), "TooManyPhotos")
        refusedWith(bodyOf(photoIds = """["nope"]"""), "MalformedPhotoId")
        refusedWith(bodyOf(owner = "\"nope\""), "MalformedNesterId")
        refusedWith(bodyOf(owner = "null"), "OwnerAbsent")
        refusedWith(bodyOf(description = "null"), "DescriptionAbsent")
        assertEquals(0, cases.storedCases())
    }

    @Test
    fun `an intake with no key is refused before anything is stored`() {
        mockMvc
            .perform(post(CASES_PATH).contentType(MediaType.APPLICATION_JSON).content(bodyOf()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.reason").value("IntakeKeyAbsent"))
        assertEquals(0, cases.storedCases())
    }

    @Test
    fun `an intake key outside the token shape is refused by its own reason`() {
        mockMvc
            .perform(
                post(CASES_PATH)
                    .header(INTAKE_KEY_HEADER, "intake key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyOf()),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.reason").value("MalformedIntakeKey"))
    }

    @Test
    fun `an intake with no body at all is refused as an absent intake rather than thrown`() {
        mockMvc
            .perform(post(CASES_PATH).header(INTAKE_KEY_HEADER, AN_INTAKE_KEY))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.reason").value("IntakeAbsent"))
    }

    @Test
    fun `a case identifier the domain refuses is a server error and not a client refusal`() {
        mockMvcFor(RefusingCaseIdentifiers())
            .perform(
                post(CASES_PATH)
                    .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyOf()),
            ).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.reason").value("CaseIdRefused"))
    }

    @Test
    fun `a body that is not the wire format is refused by the framework before the use case sees it`() {
        mockMvc
            .perform(
                post(CASES_PATH)
                    .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nesterId\":"),
            ).andExpect(status().isBadRequest)
        mockMvc
            .perform(
                post(CASES_PATH)
                    .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyOf(photoIds = "\"ph_1\"")),
            ).andExpect(status().isBadRequest)
        assertEquals(
            0,
            cases.storedCases(),
            "a body the reader cannot parse is answered by the framework rather than by the typed refusals " +
                "above, so the status is right and the body is Spring's problem detail and not a CaseRefusal. " +
                "That is a protocol failure rather than a domain one, and it is pinned here so the difference " +
                "is a decision rather than a surprise",
        )
    }

    private fun created(): String =
        mockMvc
            .perform(
                post(CASES_PATH)
                    .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyOf()),
            ).andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString

    private fun refusedWith(
        body: String,
        reason: String,
    ) {
        mockMvc
            .perform(
                post(CASES_PATH)
                    .header(INTAKE_KEY_HEADER, AN_INTAKE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.reason").value(reason))
    }
}
