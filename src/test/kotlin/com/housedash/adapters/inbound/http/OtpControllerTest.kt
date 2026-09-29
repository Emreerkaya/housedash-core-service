package com.housedash.adapters.inbound.http

import com.housedash.app.AN_IDENTIFIER
import com.housedash.app.A_CODE
import com.housedash.app.CountingOtpIdentifiers
import com.housedash.app.FixedOtpCodes
import com.housedash.app.InMemoryOtpRepository
import com.housedash.app.IssueOtp
import com.housedash.app.OTP_CLOCK
import com.housedash.app.OtpDelivery
import com.housedash.app.OtpIdentifiers
import com.housedash.app.RecordingOtpSender
import com.housedash.app.RefusingOtpIdentifiers
import com.housedash.app.TEST_HASHER
import com.housedash.app.VerifyOtp
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.assertEquals

private fun issueBody(identifier: String? = "\"$AN_IDENTIFIER\""): String = """{"identifier":$identifier}"""

private fun verifyBody(
    identifier: String? = "\"$AN_IDENTIFIER\"",
    code: String? = "\"$A_CODE\"",
): String = """{"identifier":$identifier,"code":$code}"""

class OtpControllerTest {
    private val otps = InMemoryOtpRepository()

    private val sender = RecordingOtpSender()

    private fun mockMvcFor(identifiers: OtpIdentifiers = CountingOtpIdentifiers()): MockMvc {
        val delivery = OtpDelivery(FixedOtpCodes(), TEST_HASHER, sender)
        val issueOtp = IssueOtp(otps, identifiers, delivery, OTP_CLOCK)
        val verifyOtp = VerifyOtp(otps, TEST_HASHER, OTP_CLOCK)
        return MockMvcBuilders.standaloneSetup(OtpController(issueOtp, verifyOtp)).build()
    }

    private val mockMvc: MockMvc = mockMvcFor()

    @Test
    fun `issuing for a syntactically valid identifier answers ok, and the body never carries the code`() {
        val body =
            mockMvc
                .perform(post(OTP_ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(issueBody()))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        assertEquals(false, body.contains(A_CODE))
        assertEquals(1, otps.stored.size)
    }

    @Test
    fun `issuing for an identifier nobody has ever seen answers exactly the same as one that has been`() {
        val seen =
            mockMvc
                .perform(post(OTP_ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(issueBody()))
                .andReturn()
                .response
                .contentAsString
        val neverSeen =
            mockMvc
                .perform(
                    post(OTP_ISSUE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(identifier = "\"brand-new@example.com\"")),
                ).andReturn()
                .response
                .contentAsString
        assertEquals(seen, neverSeen)
    }

    @Test
    fun `an absent identifier is refused as a bad request naming the reason`() {
        mockMvc
            .perform(
                post(OTP_ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(issueBody(identifier = "null")),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.reason").value("IdentifierAbsent"))
    }

    @Test
    fun `a malformed identifier is refused as a bad request and never echoes what was submitted`() {
        val body =
            mockMvc
                .perform(
                    post(OTP_ISSUE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issueBody(identifier = "\"not an identifier\"")),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.reason").value("MalformedIdentifier"))
                .andReturn()
                .response
                .contentAsString
        assertEquals(false, body.contains("not an identifier"))
    }

    @Test
    fun `an otp id the domain refuses is a server error and not a client refusal`() {
        mockMvcFor(RefusingOtpIdentifiers())
            .perform(post(OTP_ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(issueBody()))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.reason").value("OtpIdRefused"))
    }

    @Test
    fun `verifying the code just issued answers ok`() {
        mockMvc.perform(post(OTP_ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(issueBody()))
        mockMvc
            .perform(post(OTP_VERIFY_PATH).contentType(MediaType.APPLICATION_JSON).content(verifyBody()))
            .andExpect(status().isOk)
    }

    @Test
    fun `a wrong code, an unissued identifier, and an absent field all answer the one unauthorized refusal`() {
        mockMvc.perform(post(OTP_ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(issueBody()))
        val wrong = verifiedBody(verifyBody(code = "\"000000\""))
        val unissued = verifiedBody(verifyBody(identifier = "\"nobody@example.com\""))
        val absentCode = verifiedBody(verifyBody(code = "null"))
        assertEquals(wrong, unissued)
        assertEquals(wrong, absentCode)
        assertEquals("""{"reason":"Invalid"}""", wrong)
    }

    private fun verifiedBody(body: String): String =
        mockMvc
            .perform(post(OTP_VERIFY_PATH).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
            .andReturn()
            .response
            .contentAsString

    @Test
    fun `a body that is not the wire format is refused by the framework before the use case sees it`() {
        mockMvc
            .perform(post(OTP_ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"identifier\":"))
            .andExpect(status().isBadRequest)
        assertEquals(0, otps.stored.size)
    }

    @Test
    fun `the issue response is the same content type as the case endpoint`() {
        mockMvc
            .perform(post(OTP_ISSUE_PATH).contentType(MediaType.APPLICATION_JSON).content(issueBody()))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }
}
