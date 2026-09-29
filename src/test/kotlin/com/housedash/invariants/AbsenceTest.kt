package com.housedash.invariants

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

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
    fun `I3 tripwire, not a proof, no production name is spelled the way a bought position is`() {
        val offences = codebase.flatMap(::positionOffencesOf)
        assertTrue(offences.isEmpty()) {
            "$A_TRIPWIRE_NOT_A_PROOF I3 says position cannot be bought, so no such field may exist and quotes " +
                "are ordered by sentAt. What this test delivers is that no production name begins with one of " +
                "the ${POSITION_STEMS.size} stems listed in POSITION_STEMS. What it does not deliver is the " +
                "invariant: a ranking field named for an idea nobody listed passes it green. $THE_RESIDUAL " +
                "Remove these, or amend I3 in the domain model first:\n" + offences.joinToString("\n")
        }
    }

    @Test
    fun `the structural I3 rule, nothing in the domain orders a collection or holds a comparator`() {
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
            "this rule is the one part of I3 that is a proof rather than a tripwire: it names a shape, not a " +
                "word, so no choice of field name evades it. D166 rules that a rating may be displayed and " +
                "must never order, and a field name is not the only way to order: a comparator or a sortedBy " +
                "is the other. Quotes are ordered by sentAt at the boundary, not inside the domain. Remove " +
                "these, or amend I3 and D166 first:\n" + offences.joinToString("\n")
        }
    }

    @Test
    fun `I4 tripwire, not a proof, no type pairs a money spelling with a lead or job spelling`() {
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
            "$A_TRIPWIRE_NOT_A_PROOF I4 says taskers pay a flat subscription and are never charged per lead " +
                "or per job. What this test delivers is that no type carries one of the ${LEAD_OR_JOB.size} " +
                "words in LEAD_OR_JOB beside a name beginning with one of the ${MONEY_STEMS.size} stems in " +
                "MONEY_STEMS, anywhere on the type: its own name, a member name, a member or parameter type. " +
                "What it does not deliver is the invariant: PerLeadOutlay(leadId, outlay) passed this test " +
                "green until outlay was added to the list, and the next ordinary word for money will do the " +
                "same. $THE_RESIDUAL Remove these, or amend I4 in the domain model first:\n" +
                offences.joinToString("\n")
        }
    }

    @Test
    fun `both tripwires scan production code only, and scan this many types and named members`() {
        assertTrue(codebase.any { it.simpleName == "Case" })
        assertTrue(codebase.none { it.simpleName == "AbsenceTest" })
        assertTrue(namedMembersOf(codebase.single { it.simpleName == "CaseRow" }).isNotEmpty())
        assertTrue(signalsOf(codebase.single { it.simpleName == "CaseRow" }).contains("row"))
        val types = codebase.count()
        val members = codebase.sumOf { namedMembersOf(it).size }
        assertTrue(types >= FEWEST_PRODUCTION_TYPES) { "only $types production types were scanned" }
        assertTrue(members >= FEWEST_NAMED_MEMBERS) { "only $members named members were scanned" }
        assertTrue(POSITION_STEMS.size >= FEWEST_POSITION_STEMS)
        assertTrue(MONEY_STEMS.size >= FEWEST_MONEY_STEMS)
    }

    @Test
    fun `the position tripwire recognises the names I3 forbids`() {
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
            "position",
            "positionInResults",
            "ListingPlacement",
            "placementIndex",
            "prominence",
            "spotlightedUntil",
        ).forEach { name -> assertTrue(tokensOf(name).any(::isPositionToken), name) }
        listOf("sentAt", "description", "photos", "createdAt", "record", "keyboard", "deposit")
            .forEach { name -> assertTrue(tokensOf(name).none(::isPositionToken), name) }
    }

    @Test
    fun `the structural rule recognises an ordering call and a comparator under any name`() {
        listOf("taskerComparator", "quoteComparing", "Comparator", "byComparison").forEach { name ->
            assertTrue(tokensOf(name).any(::isComparatorToken), name)
        }
        assertTrue(ORDERING_CALLS.contains("sortedBy"))
        assertTrue(ORDERING_CALLS.contains("sortedWith"))
        assertTrue(ORDERING_CALLS.contains("compareBy"))
    }

    @Test
    fun `the money and lead tripwires recognise the shapes I4 forbids, whatever the type is called`() {
        listOf(
            "PerLeadCharge",
            "LeadFee",
            "JobChargeType",
            "SubscriptionInvoice",
            "LeadPayment",
            "amountCents",
            "PerLeadOutlay",
            "outlay",
            "leadSpend",
            "jobExpense",
            "commissionRate",
            "payoutPerJob",
        ).forEach { name -> assertTrue(tokensOf(name).any(::isMoneyToken), name) }
        listOf("PerLeadCharge", "leadId", "jobTotalCents", "perJob", "LeadPayment", "referralFee", "gigPayout")
            .forEach { name -> assertTrue(tokensOf(name).any(::isLeadOrJobToken), name) }
        listOf("Case", "Quote", "Booking", "leadingEdge", "jobbing")
            .forEach { name -> assertTrue(tokensOf(name).none(::isLeadOrJobToken), name) }
        listOf("Case", "Description", "sentAt", "position", "rating")
            .forEach { name -> assertTrue(tokensOf(name).none(::isMoneyToken), name) }
    }

    @Test
    fun `neither tripwire catches a synonym nobody listed, and a keyword list cannot be completed`() {
        listOf("exposure", "reachTier", "slotOrder", "uplift", "aboveTheFold")
            .forEach { name -> assertTrue(tokensOf(name).none(::isPositionToken), name) }
        listOf("rateCard", "dueFromTasker", "sumOwed", "theirCut", "consideration")
            .forEach { name -> assertTrue(tokensOf(name).none(::isMoneyToken), name) }
        listOf("visit", "booking", "callout", "assignment", "opportunity")
            .forEach { name -> assertTrue(tokensOf(name).none(::isLeadOrJobToken), name) }
    }

    @Test
    fun `the one field named position that is not a ranking is excluded by its qualified name`() {
        val duplicatePhoto = codebase.single { it.simpleName == "DuplicatePhoto" }
        assertTrue(tokensOf("position").any(::isPositionToken))
        assertTrue(positionOffencesOf(duplicatePhoto).isEmpty())
        val namesOfferedToTheScan =
            namedMembersOf(duplicatePhoto).map { (kind, name) -> "${duplicatePhoto.name}: $kind $name" }
        POSITION_NAMES_THAT_MEAN_SOMETHING_ELSE.forEach { excluded ->
            assertTrue(namesOfferedToTheScan.contains(excluded)) {
                "$excluded is excluded from the I3 tripwire and no longer exists, so the exclusion is now a " +
                    "silent widening of the scan's blind spot; delete it or correct it"
            }
        }
        assertTrue(
            positionOffencesOf(codebase.single { it.simpleName == "CasePhotos" }).isEmpty(),
        )
    }

    @Test
    fun `every domain package that exists announces itself to a human reviewer through CODEOWNERS`() {
        val owned =
            File(CODEOWNERS_FILE)
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.substringBefore(' ') }
                .toSet()
        val packages =
            File(DOMAIN_SOURCE_ROOT)
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && it.listFiles().orEmpty().any { file -> file.extension == "kt" } }
                .map { "/$DOMAIN_SOURCE_ROOT/${it.name}/" }
        assertTrue(packages.isNotEmpty()) { "no domain package was found under $DOMAIN_SOURCE_ROOT" }
        val unowned = packages.filterNot(owned::contains)
        assertTrue(unowned.isEmpty()) {
            "a keyword list cannot be completed, so the enforcement for a synonym nobody listed is a person " +
                "reading the diff, and CODEOWNERS is what puts them there. A domain package with no entry of " +
                "its own is reviewed under the catch-all and announces nothing, which is the half of this " +
                "guard that is not a word list. Add an entry for:\n" + unowned.joinToString("\n")
        }
    }

    private fun positionOffencesOf(type: JavaClass): List<String> =
        namedMembersOf(type)
            .filter { member -> tokensOf(member.second).any(::isPositionToken) }
            .map { (kind, name) -> "${type.name}: $kind $name" }
            .filterNot(POSITION_NAMES_THAT_MEAN_SOMETHING_ELSE::contains)

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

        const val DOMAIN_SOURCE_ROOT = "src/main/kotlin/com/housedash/domain"

        const val CODEOWNERS_FILE = "CODEOWNERS"

        const val FEWEST_PRODUCTION_TYPES = 20

        const val FEWEST_NAMED_MEMBERS = 200

        const val FEWEST_POSITION_STEMS = 13

        const val FEWEST_MONEY_STEMS = 30

        const val A_TRIPWIRE_NOT_A_PROOF =
            "this test is a tripwire, not a proof, and a keyword list cannot be completed."

        const val THE_RESIDUAL =
            "The residual is covered by a person reading the diff, which CODEOWNERS forces for every domain " +
                "package, and by the structural rule beside this one that no domain type orders a collection " +
                "or holds a comparator, which names a shape rather than a word."

        val POSITION_NAMES_THAT_MEAN_SOMETHING_ELSE =
            setOf(
                "com.housedash.domain.case.CaseError\$DuplicatePhoto: field position",
                "com.housedash.domain.case.CaseError\$DuplicatePhoto: method getPosition",
            )

        val POSITION_STEMS =
            listOf(
                "rank",
                "score",
                "promot",
                "boost",
                "featur",
                "sponsor",
                "weight",
                "priorit",
                "pinn",
                "posit",
                "placem",
                "prominen",
                "spotlight",
            )

        val MONEY_STEMS =
            listOf(
                "charge",
                "surcharge",
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
                "payout",
                "refund",
                "credit",
                "debit",
                "balance",
                "outlay",
                "spend",
                "expen",
                "levy",
                "commission",
                "margin",
                "remit",
                "disburs",
                "earn",
                "revenue",
                "wallet",
                "budget",
                "bonus",
                "bounty",
                "rebate",
                "retainer",
                "deposit",
                "stipend",
                "gratuity",
                "toll",
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

        val LEAD_OR_JOB =
            setOf(
                "lead",
                "leads",
                "job",
                "jobs",
                "referral",
                "referrals",
                "enquiry",
                "enquiries",
                "inquiry",
                "inquiries",
                "gig",
                "gigs",
                "prospect",
                "prospects",
            )
    }
}
