package com.housedash

import com.housedash.app.ApnsSender
import com.housedash.app.CaseIdentifiers
import com.housedash.app.CaseRepository
import com.housedash.app.CreateCase
import com.housedash.app.DeviceRegistrations
import com.housedash.app.DeviceTokenIdentifiers
import com.housedash.app.NotificationDrainer
import com.housedash.app.NotificationOutbox
import com.housedash.app.RandomCaseIdentifiers
import com.housedash.app.RandomDeviceTokenIdentifiers
import com.housedash.app.RegisterDeviceToken
import com.housedash.app.RevokeDeviceToken
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication(
    proxyBeanMethods = false,
    exclude = [PersistenceExceptionTranslationAutoConfiguration::class, DataSourceAutoConfiguration::class],
)
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

    @Bean
    fun deviceTokenIdentifiers(): DeviceTokenIdentifiers = RandomDeviceTokenIdentifiers()

    @Bean
    fun registerDeviceToken(
        registrations: DeviceRegistrations,
        identifiers: DeviceTokenIdentifiers,
        clock: Clock,
    ): RegisterDeviceToken = RegisterDeviceToken(registrations, identifiers, clock)

    @Bean
    fun revokeDeviceToken(
        registrations: DeviceRegistrations,
        clock: Clock,
    ): RevokeDeviceToken = RevokeDeviceToken(registrations, clock)

    @Bean
    fun notificationDrainer(
        outbox: NotificationOutbox,
        registrations: DeviceRegistrations,
        sender: ApnsSender,
        clock: Clock,
    ): NotificationDrainer = NotificationDrainer(outbox, registrations, sender, clock)
}
