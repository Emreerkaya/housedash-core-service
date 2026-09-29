package com.housedash.invariants

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

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
    fun `I3 call tripwire, not a proof, nothing in the domain names an ordering call or a comparator`() {
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
            "$A_TRIPWIRE_NOT_A_PROOF This one lists calls rather than field names, which is a second " +
                "vocabulary and not a shape: ArchUnit sees names, and an ordering can carry none. D166 rules " +
                "that a rating may be displayed and must never order, and a field name is not the only way " +
                "to order: a comparator or a sortedBy is the other. What this test delivers is that no " +
                "domain method calls one of the ${ORDERING_CALLS.size} names in ORDERING_CALLS and that no " +
                "domain member's name carries the stem compar. What it does not deliver is an ordering the " +
                "compiler inlines, which emits no named call at all: maxBy { it.rating } over an Int key " +
                "compiles to an iterator loop and one integer comparison and walks this rule green, and " +
                "$ORDERING_CALLS_THE_COMPILER_INLINES are themselves inline in the standard library, so " +
                "${ORDERING_CALLS_THE_COMPILER_INLINES.size} of the listed names can never appear in " +
                "bytecode at all. $THE_RESIDUAL Quotes " +
                "are ordered by sentAt at the boundary, not inside the domain. Remove these, or amend I3 " +
                "and D166 first:\n" + offences.joinToString("\n")
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
    fun `both tripwires scan production code only, and scan exactly this many types and named members`() {
        assertTrue(codebase.any { it.simpleName == "Case" })
        assertTrue(codebase.none { it.simpleName == "AbsenceTest" })
        assertTrue(namedMembersOf(codebase.single { it.simpleName == "CaseRow" }).isNotEmpty())
        assertTrue(signalsOf(codebase.single { it.simpleName == "CaseRow" }).contains("row"))
        val types = codebase.count()
        val members = codebase.sumOf { namedMembersOf(it).size }
        assertEquals(
            PRODUCTION_TYPES_THE_SCAN_READS,
            types,
            "a floor asks whether the scan still reads a lot; the question is whether it still reads what it " +
                "read, and the floor this replaces was twenty against sixty-eight, so forty-eight types could " +
                "leave without anything noticing. The count moves with every commit that adds or removes a " +
                "type and moving it here is that commit's own edit",
        )
        assertEquals(
            NAMED_MEMBERS_THE_SCAN_READS,
            members,
            "the same reason as the type count above: a floor cannot see a member leave the scan",
        )
    }

    @Test
    fun `every keyword table these tripwires read holds exactly the entries this test names`() {
        assertEquals(POSITION_STEMS_THE_TABLE_HOLDS.split(" "), POSITION_STEMS, TABLE_CONTENTS_NOT_SIZE)
        assertEquals(MONEY_STEMS_THE_TABLE_HOLDS.split(" "), MONEY_STEMS, TABLE_CONTENTS_NOT_SIZE)
        assertEquals(ORDERING_CALLS_THE_TABLE_HOLDS.split(" ").toSet(), ORDERING_CALLS, TABLE_CONTENTS_NOT_SIZE)
        assertEquals(LEAD_OR_JOB_THE_TABLE_HOLDS.split(" ").toSet(), LEAD_OR_JOB, TABLE_CONTENTS_NOT_SIZE)
        assertEquals(
            ORDERING_CALLS_THE_COMPILER_INLINES_THE_TABLE_HOLDS.split(" ").toSet(),
            ORDERING_CALLS_THE_COMPILER_INLINES,
            TABLE_CONTENTS_NOT_SIZE,
        )
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
    fun `the ordering tripwire recognises an ordering call and a comparator under any name`() {
        listOf("taskerComparator", "quoteComparing", "Comparator", "byComparison").forEach { name ->
            assertTrue(tokensOf(name).any(::isComparatorToken), name)
        }
        assertTrue(ORDERING_CALLS.contains("sortedBy"))
        assertTrue(ORDERING_CALLS.contains("sortedWith"))
        assertTrue(ORDERING_CALLS.contains("compareBy"))
    }

    @Test
    fun `the ordering tripwire lists names the compiler inlines, which no bytecode can ever carry`() {
        assertTrue(ORDERING_CALLS.containsAll(ORDERING_CALLS_THE_COMPILER_INLINES))
        assertTrue(ORDERING_CALLS_THE_COMPILER_INLINES.isNotEmpty())
        assertTrue(!ORDERING_CALLS.contains("maxBy")) {
            "maxBy was added to ORDERING_CALLS, which does not close the gap the failure message names: it " +
                "is inline, so the call never reaches bytecode and the rule stays green on it"
        }
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
    fun `every domain package on disk at any depth is covered by a CODEOWNERS entry that names an owner`() {
        val owned = pathsWithAnOwnerIn(File(CODEOWNERS_FILE).readLines())
        val packages = packagesUnder(File(DOMAIN_SOURCE_ROOT), "/$DOMAIN_SOURCE_ROOT/")
        assertTrue(packages.isNotEmpty()) { "no domain package was found under $DOMAIN_SOURCE_ROOT" }
        val unowned = packages.filterNot { source -> owned.any(source::startsWith) }
        assertTrue(unowned.isEmpty()) {
            "a keyword list cannot be completed, and this guard is the half of the answer that is not a " +
                "word list: every directory under $DOMAIN_SOURCE_ROOT that holds Kotlin at any depth is " +
                "named by a CODEOWNERS entry that carries at least one owner after the path. A path with no " +
                "owner after it does not satisfy this and must not: a more specific pattern with an empty " +
                "owner list takes the package out of the catch-all it was under, which is worse than having " +
                "no line at all. What this guard does not deliver is a reviewer. $THE_RESIDUAL An entry " +
                "pointing at a package that does not exist is deliberately not checked, because the " +
                "forward declarations are intentional. Add an entry with an owner for:\n" +
                unowned.joinToString("\n")
        }
    }

    @Test
    fun `the CODEOWNERS guard rejects an entry with no owner and sees a package one directory deeper`() {
        assertTrue(pathsWithAnOwnerIn(listOf("/domain/matching/")).isEmpty())
        assertTrue(pathsWithAnOwnerIn(listOf("/domain/matching/   ")).isEmpty())
        assertTrue(pathsWithAnOwnerIn(listOf("/domain/matching/ # @Emreerkaya")).isEmpty())
        assertEquals(setOf("/domain/matching/"), pathsWithAnOwnerIn(listOf("/domain/matching/ @Emreerkaya")))
        val root = Files.createTempDirectory("codeowners-guard").toFile()
        File(root, "matching/rules").mkdirs()
        File(root, "matching/rules/Rules.kt").writeText("package com.housedash.domain.matching.rules\n")
        val planted = packagesUnder(root, "/$DOMAIN_SOURCE_ROOT/")
        assertEquals(
            listOf("/$DOMAIN_SOURCE_ROOT/matching/", "/$DOMAIN_SOURCE_ROOT/matching/rules/"),
            planted.sorted(),
        )
        assertTrue(planted.none { source -> pathsWithAnOwnerIn(listOf("/domain/matching/")).any(source::startsWith) })
        root.deleteRecursively()
    }

    @Test
    fun `the mechanism both tripwires hand their residual to is required by the merge gate for this layer`() {
        val gate = File(MERGE_GATE_SCRIPT).readText()
        assertTrue(THE_RESIDUAL.contains(MERGE_GATE_SCRIPT)) {
            "the residual clause no longer names the file this test reads, so the two can drift apart"
        }
        assertTrue(gate.contains(INVARIANTS_REQUIRED)) {
            "$MERGE_GATE_SCRIPT no longer adds the invariants dimension to the required set, so both " +
                "tripwires now hand their residual to a mechanism that does not fire, which is D178 again"
        }
        assertTrue(gate.contains(DOMAIN_DIFF_TRIGGER)) {
            "$MERGE_GATE_SCRIPT no longer keys the invariants dimension on a diff under $DOMAIN_SOURCE_ROOT, " +
                "so a change to this layer can be merged with no invariants review"
        }
    }

    private fun pathsWithAnOwnerIn(lines: List<String>): Set<String> =
        lines
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { it.split(WHITESPACE) }
            .filter { tokens -> tokens.drop(1).any { it.startsWith(OWNER_PREFIX) } }
            .map { it.first() }
            .toSet()

    private fun packagesUnder(
        root: File,
        entryPrefix: String,
    ): List<String> =
        root
            .walkTopDown()
            .filter { it.isDirectory && it != root }
            .filter { directory -> directory.walkTopDown().any { it.isFile && it.extension == "kt" } }
            .map { entryPrefix + it.toRelativeString(root).replace(File.separatorChar, '/') + "/" }
            .toList()

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

        const val OWNER_PREFIX = "@"

        const val MERGE_GATE_SCRIPT = "scripts/agent-review.sh"

        const val INVARIANTS_REQUIRED = "required+=(invariants)"

        const val DOMAIN_DIFF_TRIGGER = "^\"?src/[^/]+/kotlin/com/housedash/domain/"

        val WHITESPACE = Regex("""\s+""")

        const val PRODUCTION_TYPES_THE_SCAN_READS = 72

        const val NAMED_MEMBERS_THE_SCAN_READS = 1197

        const val TABLE_CONTENTS_NOT_SIZE =
            "a size floor asks whether a table is still big and the question is whether it is still the set " +
                "the tripwire was written against. An entry that leaves is invisible to a floor and to a loop " +
                "over the table alike, because the loop simply stops visiting it, so the contents are pinned " +
                "here and moving them is a deliberate edit with a case beside it"

        const val POSITION_STEMS_THE_TABLE_HOLDS =
            "rank score promot boost featur sponsor weight priorit pinn posit placem prominen spotlight"

        const val MONEY_STEMS_THE_TABLE_HOLDS =
            "charge surcharge fee subscription invoice billing tariff amount price cost cent total money " +
                "payment payable paid payout refund credit debit balance outlay spend expen levy commission " +
                "margin remit disburs earn revenue wallet budget bonus bounty rebate retainer deposit " +
                "stipend gratuity toll"

        const val ORDERING_CALLS_THE_TABLE_HOLDS =
            "sorted sortedBy sortedByDescending sortedDescending sortedWith sortBy sortByDescending sortWith " +
                "sort maxByOrNull minByOrNull maxWithOrNull minWithOrNull compareBy thenBy"

        const val ORDERING_CALLS_THE_COMPILER_INLINES_THE_TABLE_HOLDS = "maxByOrNull minByOrNull compareBy thenBy"

        const val LEAD_OR_JOB_THE_TABLE_HOLDS =
            "lead leads job jobs referral referrals enquiry enquiries inquiry inquiries gig gigs prospect prospects"

        const val A_TRIPWIRE_NOT_A_PROOF =
            "this test is a tripwire, not a proof, and a keyword list cannot be completed."

        const val THE_RESIDUAL =
            "The residual belongs to the one mechanism on this repository that blocks a merge on a human " +
                "judgement: the invariants review dimension, which scripts/agent-review.sh requires of " +
                "every pull request whose diff touches src/*/kotlin/com/housedash/domain/ and without " +
                "which the merge gate exits non-zero. It is not covered by CODEOWNERS, which D178 measured " +
                "as require_code_owner_review false, zero required approvals and one collaborator who is " +
                "every pull request's author, so an entry names an owner and summons nobody."

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

        val ORDERING_CALLS_THE_COMPILER_INLINES = setOf("maxByOrNull", "minByOrNull", "compareBy", "thenBy")

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
