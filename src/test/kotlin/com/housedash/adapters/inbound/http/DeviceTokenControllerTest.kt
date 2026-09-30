package com.housedash.adapters.inbound.http

import com.housedash.adapters.outbound.persistence.InMemoryDeviceRegistrationRepository
import com.housedash.app.CountingDeviceTokenIdentifiers
import com.housedash.app.FIXED_CLOCK
import com.housedash.app.RefusingDeviceTokenIdentifiers
import com.housedash.app.RegisterDeviceToken
import com.housedash.app.RevokeDeviceToken
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.assertEquals

private fun bodyOf(
    owner: String = "\"ns_1\"",
    device: String = "\"dv_1\"",
    token: String = "\"a1b2c3\"",
): String = """{"nesterId":$owner,"deviceId":$device,"token":$token}"""

private fun MockMvc.register(body: String): ResultActions =
    perform(post(DEVICE_TOKENS_PATH).contentType(MediaType.APPLICATION_JSON).content(body))

class DeviceTokenControllerTest {
    private val registrations = InMemoryDeviceRegistrationRepository()

    private val mockMvc: MockMvc = mockMvcFor(CountingDeviceTokenIdentifiers())

    private fun mockMvcFor(identifiers: com.housedash.app.DeviceTokenIdentifiers): MockMvc =
        MockMvcBuilders
            .standaloneSetup(
                DeviceTokenController(
                    RegisterDeviceToken(registrations, identifiers, FIXED_CLOCK),
                    RevokeDeviceToken(registrations, FIXED_CLOCK),
                ),
            ).build()

    @Test
    fun `registering a device answers created with the minted id`() {
        mockMvc
            .register(bodyOf())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.deviceTokenId").exists())
            .andExpect(jsonPath("$.replaced").value(false))
        assertEquals(1, registrations.registeredCount())
    }

    @Test
    fun `re-registering the same device answers ok and replaces rather than duplicates`() {
        mockMvc.register(bodyOf(token = "\"old\""))
        mockMvc
            .register(bodyOf(token = "\"new\""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replaced").value(true))
        assertEquals(1, registrations.registeredCount())
    }

    @Test
    fun `an absent field is refused with its own reason`() {
        mockMvc
            .register(bodyOf(token = "null"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.reason").value("TokenAbsent"))
    }

    @Test
    fun `a malformed device id is refused as a client error`() {
        mockMvc
            .register(bodyOf(device = "\"nope\""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.reason").value("MalformedDeviceId"))
    }

    @Test
    fun `an identifier the domain refuses is a server error`() {
        mockMvcFor(RefusingDeviceTokenIdentifiers())
            .register(bodyOf())
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.reason").value("DeviceTokenIdRefused"))
    }

    @Test
    fun `revoking a registered device answers no content`() {
        mockMvc.register(bodyOf())
        mockMvc
            .perform(delete(DEVICE_TOKENS_PATH).param("nesterId", "ns_1").param("deviceId", "dv_1"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `revoking a device that was never registered answers not found`() {
        mockMvc
            .perform(delete(DEVICE_TOKENS_PATH).param("nesterId", "ns_1").param("deviceId", "dv_1"))
            .andExpect(status().isNotFound)
    }
}
