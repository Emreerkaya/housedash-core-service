package com.housedash

import com.housedash.adapters.outbound.otp.LoggingOtpSender
import com.housedash.adapters.outbound.persistence.InMemoryCaseRepository
import com.housedash.app.CaseIdentifiers
import com.housedash.app.CaseRepository
import com.housedash.app.CreateCase
import com.housedash.app.IssueOtp
import com.housedash.app.OtpCodeGenerator
import com.housedash.app.OtpDelivery
import com.housedash.app.OtpHasher
import com.housedash.app.OtpIdentifiers
import com.housedash.app.OtpRepository
import com.housedash.app.OtpSender
import com.housedash.app.RandomCaseIdentifiers
import com.housedash.app.RandomOtpIdentifiers
import com.housedash.app.SecureRandomOtpCodes
import com.housedash.app.Sha256OtpHasher
import com.housedash.app.VerifyOtp
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

private const val DEV_ONLY_DEFAULT_PEPPER = "dev-only-pepper-change-me"

@SpringBootApplication(proxyBeanMethods = false)
class HouseDashService {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun caseIdentifiers(): CaseIdentifiers = RandomCaseIdentifiers()

    @Bean
    fun caseRepository(): CaseRepository = InMemoryCaseRepository()

    @Bean
    fun createCase(
        cases: CaseRepository,
        identifiers: CaseIdentifiers,
        clock: Clock,
    ): CreateCase = CreateCase(cases, identifiers, clock)

    @Bean
    fun otpIdentifiers(): OtpIdentifiers = RandomOtpIdentifiers()

    @Bean
    fun otpCodeGenerator(): OtpCodeGenerator = SecureRandomOtpCodes()

    @Bean
    fun otpHasher(
        @Value("\${otp.hash-pepper:$DEV_ONLY_DEFAULT_PEPPER}") pepper: String,
    ): OtpHasher = Sha256OtpHasher(pepper)

    @Bean
    fun otpSender(): OtpSender = LoggingOtpSender()

    @Bean
    fun otpDelivery(
        codes: OtpCodeGenerator,
        hasher: OtpHasher,
        sender: OtpSender,
    ): OtpDelivery = OtpDelivery(codes, hasher, sender)

    @Bean
    fun issueOtp(
        otps: OtpRepository,
        identifiers: OtpIdentifiers,
        delivery: OtpDelivery,
        clock: Clock,
    ): IssueOtp = IssueOtp(otps, identifiers, delivery, clock)

    @Bean
    fun verifyOtp(
        otps: OtpRepository,
        hasher: OtpHasher,
        clock: Clock,
    ): VerifyOtp = VerifyOtp(otps, hasher, clock)
}
