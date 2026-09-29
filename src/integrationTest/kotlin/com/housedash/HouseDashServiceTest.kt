package com.housedash

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.housedash.adapters.inbound.http.CASES_PATH
import com.housedash.adapters.inbound.http.INTAKE_KEY_HEADER
import com.housedash.adapters.inbound.http.OTP_ISSUE_PATH
import com.housedash.adapters.inbound.http.OTP_VERIFY_PATH
import com.housedash.adapters.outbound.otp.LoggingOtpSender
import com.housedash.adapters.outbound.persistence.InMemoryCaseRepository
import com.housedash.adapters.outbound.persistence.PostgresAccountRepository
import com.housedash.adapters.outbound.persistence.PostgresOtpRepository
import com.housedash.app.AccountRepository
import com.housedash.app.CaseIdentifiers
import com.housedash.app.CaseRepository
import com.housedash.app.CreateCase
import com.housedash.app.IssueOtp
import com.housedash.app.OtpRepository
import com.housedash.app.VerifyOtp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
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
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val POSTGRES_IMAGE = "postgres:16"

private const val TAP_DESCRIPTION = "kitchen tap drips from the base and needs a look"

private val CODE_IN_THE_DELIVERY_LOG = Pattern.compile("is (\\d{6})$")

@Testcontainers
@SpringBootTest
class HouseDashServiceTest
    @Autowired
    constructor(
        private val context: WebApplicationContext,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        private val mockMvc: MockMvc get() = MockMvcBuilders.webAppContextSetup(context).build()

        private fun <T : Any> beanOfType(type: Class<T>): T = context.getBean(type)

        @AfterEach
        fun cleanUp() {
            jdbcTemplate.update("DELETE FROM otp_codes")
            jdbcTemplate.update("DELETE FROM account_profiles")
            jdbcTemplate.update("DELETE FROM accounts")
        }

        @Test
        fun `the service wires case to the in memory repository and identity to postgres`() {
            assertIs<InMemoryCaseRepository>(beanOfType(CaseRepository::class.java))
            assertIs<PostgresOtpRepository>(beanOfType(OtpRepository::class.java))
            assertIs<PostgresAccountRepository>(beanOfType(AccountRepository::class.java))
            assertEquals(Clock.systemUTC(), beanOfType(Clock::class.java))
            assertIs<CreateCase>(beanOfType(CreateCase::class.java))
            assertIs<CaseIdentifiers>(beanOfType(CaseIdentifiers::class.java))
            assertIs<IssueOtp>(beanOfType(IssueOtp::class.java))
            assertIs<VerifyOtp>(beanOfType(VerifyOtp::class.java))
        }

        @Test
        fun `posting a described case to the running service creates it, so the endpoint answers something`() {
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
            val cases = assertIs<InMemoryCaseRepository>(beanOfType(CaseRepository::class.java))
            assertEquals(1, cases.storedCases())
        }

        @Test
        fun `an issued code read from the delivery log verifies against the real database`() {
            val appender = ListAppender<ILoggingEvent>()
            appender.start()
            val logger = LoggerFactory.getLogger(LoggingOtpSender::class.java) as Logger
            logger.addAppender(appender)
            try {
                mockMvc
                    .perform(
                        post(OTP_ISSUE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"identifier":"wired-otp@example.com"}"""),
                    ).andExpect(status().isOk)
                val logged = appender.list.single().formattedMessage
                val matcher = CODE_IN_THE_DELIVERY_LOG.matcher(logged)
                assertTrue(matcher.find(), "the dev sender's own log line is where a demo reads the code: $logged")
                val code = matcher.group(1)
                mockMvc
                    .perform(
                        post(OTP_VERIFY_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"identifier":"wired-otp@example.com","code":"$code"}"""),
                    ).andExpect(status().isOk)
            } finally {
                logger.detachAppender(appender)
            }
        }

        private companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
        }
    }
