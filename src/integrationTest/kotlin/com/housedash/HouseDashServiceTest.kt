package com.housedash

import com.housedash.adapters.inbound.http.CASES_PATH
import com.housedash.adapters.inbound.http.INTAKE_KEY_HEADER
import com.housedash.adapters.outbound.persistence.PostgresCaseRepository
import com.housedash.app.CaseIdentifiers
import com.housedash.app.CaseRepository
import com.housedash.app.CreateCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val POSTGRES_IMAGE = "postgres:16"

private const val TAP_DESCRIPTION = "kitchen tap drips from the base and needs a look"

@Testcontainers
@SpringBootTest
class HouseDashServiceTest
    @Autowired
    constructor(
        private val context: WebApplicationContext,
        private val cases: CaseRepository,
        private val clock: Clock,
        private val identifiers: CaseIdentifiers,
        private val createCase: CreateCase,
    ) {
        private val mockMvc: MockMvc get() = MockMvcBuilders.webAppContextSetup(context).build()

        @Test
        fun `the service wires the port to the postgres repository, the clock and the identifier source`() {
            assertIs<PostgresCaseRepository>(cases)
            assertEquals(Clock.systemUTC(), clock)
            assertIs<CreateCase>(createCase)
            assertIs<CaseIdentifiers>(identifiers)
        }

        @Test
        fun `posting a described case to the running service creates it against the real database`() {
            mockMvc
                .perform(
                    post(CASES_PATH)
                        .header(INTAKE_KEY_HEADER, "wired-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"nesterId":"ns_1","description":"$TAP_DESCRIPTION","photoIds":["ph_1"]}""",
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.state").value("DESCRIBED"))
        }

        private companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
        }
    }
