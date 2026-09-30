package com.housedash

import com.housedash.adapters.inbound.http.CASES_PATH
import com.housedash.adapters.inbound.http.INTAKE_KEY_HEADER
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals

private const val POSTGRES_IMAGE = "postgres:16"

private const val SESSION_USER = "postgres.projectref"

private const val CASE_BODY =
    """{"nesterId":"ns_1","description":"kitchen tap drips from the base and needs a look","photoIds":["ph_1"]}"""

@Testcontainers
@SpringBootTest
@ActiveProfiles("supabase")
class SupabaseProfileTest
    @Autowired
    constructor(
        private val context: WebApplicationContext,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @Test
        fun `the supabase profile alone migrates the schema and stores a posted case`() {
            MockMvcBuilders
                .webAppContextSetup(context)
                .build()
                .perform(
                    post(CASES_PATH)
                        .header(INTAKE_KEY_HEADER, "supabase-profile-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CASE_BODY),
                ).andExpect(status().isCreated)

            assertEquals(
                listOf("1"),
                jdbcTemplate.queryForList(
                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                    String::class.java,
                ),
            )
            assertEquals(
                1,
                jdbcTemplate.queryForObject("SELECT count(*) FROM cases WHERE owner = 'ns_1'", Int::class.java),
            )
            assertEquals(SESSION_USER, jdbcTemplate.queryForObject("SELECT current_user", String::class.java))
        }

        companion object {
            @Container
            @JvmStatic
            val postgres: PostgreSQLContainer =
                PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                    .withUsername(SESSION_USER)

            @DynamicPropertySource
            @JvmStatic
            fun supabaseVariables(registry: DynamicPropertyRegistry) {
                registry.add("SUPABASE_DB_HOST") { postgres.host }
                registry.add("SUPABASE_DB_PORT") { postgres.firstMappedPort.toString() }
                registry.add("SUPABASE_DB_NAME") { postgres.databaseName }
                registry.add("SUPABASE_DB_USER") { postgres.username }
                registry.add("SUPABASE_DB_PASSWORD") { postgres.password }
                registry.add("SUPABASE_DB_SSLMODE") { "disable" }
            }
        }
    }
