package com.housedash.invariants

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AbsenceTest {
    private val productionOnly =
        ImportOption { location: Location ->
            ImportOption.DoNotIncludeTests().includes(location) &&
                TEST_OUTPUTS.none { location.contains(it) }
        }

    private val codebase =
        ClassFileImporter()
            .withImportOption(productionOnly)
            .importPackages("com.housedash")

    @Test
    fun `no type anywhere carries a name that could rank, promote or boost a listing`() {
        val offences =
            codebase.flatMap { type ->
                namedMembersOf(type)
                    .filter { member -> tokensOf(member.second).any(::isPositionToken) }
                    .map { (kind, name) -> "${type.name}: $kind $name" }
            }
        assertTrue(offences.isEmpty()) {
            "invariant I3 is enforced by absence: position cannot be bought, so no such field may exist and " +
                "quotes are ordered by sentAt. Remove these, or amend I3 in the domain model first:\n" +
                offences.joinToString("\n")
        }
    }

    @Test
    fun `no charge type anywhere names a lead or a job`() {
        val chargeTypes = codebase.filter { tokensOf(it.simpleName).any(::isChargeToken) }
        val byName =
            chargeTypes
                .filter { tokensOf(it.simpleName).any(::isLeadOrJobToken) }
                .map { "${it.name}: the type name itself pairs a charge with a lead or a job" }
        val byMember =
            chargeTypes.flatMap { type ->
                namedMembersOf(type)
                    .filter { member -> tokensOf(member.second).any(::isLeadOrJobToken) }
                    .map { (kind, name) -> "${type.name}: $kind $name" }
            }
        val offences = byName + byMember
        assertTrue(offences.isEmpty()) {
            "invariant I4 is enforced by absence: taskers pay a flat subscription and are never charged per lead " +
                "or per job, so no charge type may name one. Remove these, or amend I4 in the domain model " +
                "first:\n" + offences.joinToString("\n")
        }
    }

    @Test
    fun `both absence rules scan production code, and only production code`() {
        assertTrue(codebase.any())
        assertTrue(codebase.any { it.simpleName == "Case" })
        assertTrue(codebase.none { it.simpleName == "AbsenceTest" })
        assertTrue(namedMembersOf(codebase.single { it.simpleName == "CaseRow" }).isNotEmpty())
    }

    @Test
    fun `the position token test recognises the four forbidden names and their inflections`() {
        listOf("rank", "score", "promoted", "boost", "searchRank", "scores", "promotion", "boostedUntil")
            .forEach { name -> assertTrue(tokensOf(name).any(::isPositionToken), name) }
        listOf("sentAt", "description", "photos", "createdAt", "record", "keyboard")
            .forEach { name -> assertTrue(tokensOf(name).none(::isPositionToken), name) }
    }

    @Test
    fun `the charge and lead token tests recognise the shapes I4 forbids`() {
        listOf("PerLeadCharge", "LeadFee", "JobChargeType", "SubscriptionInvoice")
            .forEach { name -> assertTrue(tokensOf(name).any(::isChargeToken), name) }
        listOf("PerLeadCharge", "leadId", "jobTotalCents", "perJob")
            .forEach { name -> assertTrue(tokensOf(name).any(::isLeadOrJobToken), name) }
        listOf("Case", "Quote", "Booking", "leadingEdge", "jobbing")
            .forEach { name -> assertTrue(tokensOf(name).none(::isLeadOrJobToken), name) }
    }

    private fun namedMembersOf(type: JavaClass): List<Pair<String, String>> =
        type.fields.map { "field" to it.name } +
            type.methods.map { "method" to it.name } +
            type.constructors.flatMap { constructor ->
                constructor.parameterTypes.map { "constructor parameter of type" to it.name }
            }

    private fun tokensOf(name: String): List<String> =
        name
            .split(Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

    private fun isPositionToken(token: String): Boolean = POSITION_STEMS.any { token.startsWith(it) }

    private fun isChargeToken(token: String): Boolean = CHARGE_STEMS.any { token.startsWith(it) }

    private fun isLeadOrJobToken(token: String): Boolean = LEAD_OR_JOB.contains(token)

    private companion object {
        val TEST_OUTPUTS = listOf("/classes/kotlin/test/", "/classes/kotlin/integrationTest/")

        val POSITION_STEMS = listOf("rank", "score", "promot", "boost")

        val CHARGE_STEMS = listOf("charge", "fee", "subscription", "invoice", "billing", "tariff")

        val LEAD_OR_JOB = setOf("lead", "leads", "job", "jobs")
    }
}
