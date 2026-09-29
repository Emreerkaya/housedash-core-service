package com.housedash

import com.housedash.app.CaseIdentifiers
import com.housedash.app.CaseRepository
import com.housedash.app.CreateCase
import com.housedash.app.RandomCaseIdentifiers
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication(proxyBeanMethods = false)
class HouseDashService {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun caseIdentifiers(): CaseIdentifiers = RandomCaseIdentifiers()

    @Bean
    fun createCase(
        cases: CaseRepository,
        identifiers: CaseIdentifiers,
        clock: Clock,
    ): CreateCase = CreateCase(cases, identifiers, clock)
}
