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
    fun `nothing in the domain orders a collection or holds a comparator`() {
        val offences =
            codebase
                .filter { it.packageName.startsWith(DOMAIN_PACKAGE) }
                .flatMap { type ->
                    type.methodCallsFromSelf
                        .filter { it.target.name in ORDERING_CALLS }
                        .map { "${type.name}: calls ${it.target.name} at ${it.sourceCodeLocation}" } +
                        namedMembersOf(type)
                            .filter { member -> tokensOf(member.second).any(::isComparatorToken) }
                            .map { (kind, name) -> "${type.name}: $kind $name" }
                }
        assertTrue(offences.isEmpty()) {
            "D166 rules that a rating may be displayed and must never order, and I3 says position cannot be " +
                "bought. A field name is not the only way to order: a comparator or a sortedBy is the other. " +
                "Quotes are ordered by sentAt at the boundary, not inside the domain. Remove these, or amend " +
                "I3 and D166 first:\n" + offences.joinToString("\n")
        }
    }

    @Test
    fun `no type anywhere pairs a money shape with a lead or a job`() {
        val offences =
            codebase.mapNotNull { type ->
                val signals = signalsOf(type)
                val leadOrJob = signals.filter(::isLeadOrJobToken).distinct()
                val money = signals.filter(::isMoneyToken).distinct()
                if (leadOrJob.isEmpty() || money.isEmpty()) {
                    null
                } else {
                    "${type.name}: names $leadOrJob beside $money"
                }
            }
        assertTrue(offences.isEmpty()) {
            "invariant I4 is enforced by absence: taskers pay a flat subscription and are never charged per lead " +
                "or per job. The shape that matters is a money-shaped member beside a lead or job reference, " +
                "whatever the type is called. Remove these, or amend I4 in the domain model first:\n" +
                offences.joinToString("\n")
        }
    }

    @Test
    fun `both absence rules scan production code, and only production code`() {
        assertTrue(codebase.any())
        assertTrue(codebase.any { it.simpleName == "Case" })
        assertTrue(codebase.none { it.simpleName == "AbsenceTest" })
        assertTrue(namedMembersOf(codebase.single { it.simpleName == "CaseRow" }).isNotEmpty())
        assertTrue(signalsOf(codebase.single { it.simpleName == "CaseRow" }).contains("row"))
    }

    @Test
    fun `the position and comparator tests recognise the names I3 and D166 forbid`() {
        listOf(
            "rank",
            "score",
            "promoted",
            "boost",
            "searchRank",
            "scores",
            "promotion",
            "boostedUntil",
            "featured",
            "sponsoredUntil",
            "visibilityWeight",
            "priority",
            "pinnedTasker",
        ).forEach { name -> assertTrue(tokensOf(name).any(::isPositionToken), name) }
        listOf("sentAt", "description", "photos", "createdAt", "record", "keyboard", "position")
            .forEach { name -> assertTrue(tokensOf(name).none(::isPositionToken), name) }
        listOf("taskerComparator", "quoteComparing").forEach { name ->
            assertTrue(tokensOf(name).any(::isComparatorToken), name)
        }
        assertTrue(ORDERING_CALLS.contains("sortedBy"))
        assertTrue(ORDERING_CALLS.contains("sortedWith"))
    }

    @Test
    fun `the money and lead token tests recognise the shapes I4 forbids, whatever the type is called`() {
        listOf("PerLeadCharge", "LeadFee", "JobChargeType", "SubscriptionInvoice", "LeadPayment", "amountCents")
            .forEach { name -> assertTrue(tokensOf(name).any(::isMoneyToken), name) }
        listOf("PerLeadCharge", "leadId", "jobTotalCents", "perJob", "LeadPayment")
            .forEach { name -> assertTrue(tokensOf(name).any(::isLeadOrJobToken), name) }
        listOf("Case", "Quote", "Booking", "leadingEdge", "jobbing")
            .forEach { name -> assertTrue(tokensOf(name).none(::isLeadOrJobToken), name) }
        listOf("Case", "Description", "sentAt", "position", "rating")
            .forEach { name -> assertTrue(tokensOf(name).none(::isMoneyToken), name) }
    }

    private fun namedMembersOf(type: JavaClass): List<Pair<String, String>> =
        type.fields.map { "field" to it.name } +
            type.fields.map { "field type" to it.rawType.name } +
            type.methods.map { "method" to it.name } +
            type.methods.map { "method return type" to it.rawReturnType.name } +
            type.constructors.flatMap { constructor ->
                constructor.rawParameterTypes.map { "constructor parameter of type" to it.name }
            }

    private fun signalsOf(type: JavaClass): List<String> {
        val memberTokens = namedMembersOf(type).flatMap { tokensOf(it.second) }
        return tokensOf(type.simpleName) + memberTokens
    }

    private fun tokensOf(name: String): List<String> =
        name
            .split(Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

    private fun isPositionToken(token: String): Boolean = POSITION_STEMS.any { token.startsWith(it) }

    private fun isMoneyToken(token: String): Boolean = MONEY_STEMS.any { token.startsWith(it) }

    private fun isComparatorToken(token: String): Boolean = token.startsWith("compar")

    private fun isLeadOrJobToken(token: String): Boolean = LEAD_OR_JOB.contains(token)

    private companion object {
        val TEST_OUTPUTS = listOf("/classes/kotlin/test/", "/classes/kotlin/integrationTest/")

        const val DOMAIN_PACKAGE = "com.housedash.domain"

        val POSITION_STEMS =
            listOf("rank", "score", "promot", "boost", "featur", "sponsor", "weight", "priorit", "pinn")

        val MONEY_STEMS =
            listOf(
                "charge",
                "fee",
                "subscription",
                "invoice",
                "billing",
                "tariff",
                "amount",
                "price",
                "cost",
                "cent",
                "total",
                "money",
                "payment",
                "payable",
                "paid",
                "refund",
                "credit",
                "debit",
                "balance",
            )

        val ORDERING_CALLS =
            setOf(
                "sorted",
                "sortedBy",
                "sortedByDescending",
                "sortedDescending",
                "sortedWith",
                "sortBy",
                "sortByDescending",
                "sortWith",
                "sort",
                "maxByOrNull",
                "minByOrNull",
                "maxWithOrNull",
                "minWithOrNull",
                "compareBy",
                "thenBy",
            )

        val LEAD_OR_JOB = setOf("lead", "leads", "job", "jobs")
    }
}
