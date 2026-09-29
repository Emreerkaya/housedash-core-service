package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private const val FIRST_PRINTABLE_ASCII = 0x20

private const val LAST_PRINTABLE_ASCII = 0x7E

private val MARKS_OUTSIDE_ASCII_A_NUMBER_IS_WRITTEN_WITH =
    listOf('\u3002', '\uFF61', '\u2022', '\u00A0', '\u2013', '\u2212', '\u2044', '\u30FB')

private val MARKS_A_NUMBER_MAY_BE_WRITTEN_WITH =
    ((FIRST_PRINTABLE_ASCII..LAST_PRINTABLE_ASCII).map { it.toChar() } + MARKS_OUTSIDE_ASCII_A_NUMBER_IS_WRITTEN_WITH)
        .filterNot { it.isLetterOrDigit() }

private val PADDINGS_A_SEPARATOR_MAY_CARRY =
    listOf<(Char) -> String>({ "$it " }, { " $it" }, { " $it " }, { "$it  " }, { "  $it  " })

private fun groupedWith(mark: Char): String = "call me on 917${mark}555${mark}0199 about the leak"

private const val DIALABLE_DIGITS = "9175550199"

private fun spelledOneDigitTo(separators: List<String>): String =
    DIALABLE_DIGITS
        .mapIndexed { index, digit ->
            if (index == 0) digit.toString() else separators[(index - 1) % separators.size] + digit
        }.joinToString("") + " is my cell, please ring about the radiator"

private fun isFound(text: String): Boolean = contactDetailsIn(text).isNotEmpty()

private fun spelling(separators: List<String>): String =
    separators.joinToString("/") { separator -> separator.map { "U+%04X".format(it.code) }.joinToString("") }

class NumberShapeFilterTest {
    @Test
    fun `every arm that reads a separator between two digit groups reads the same marks as every other`() {
        val disagreeing =
            MARKS_A_NUMBER_MAY_BE_WRITTEN_WITH.filter { mark ->
                val grouped = isFound(groupedWith(mark))
                val spelled = isFound(spelledOneDigitTo(listOf(mark.toString())))
                grouped != spelled
            }
        assertEquals(
            emptyList(),
            disagreeing,
            "a mark is either something that can stand between two digits or it is not, and the answer has to " +
                "be the same for every arm that reads one, stated once: a separator is any single character " +
                "that is neither a letter nor a digit, and a run grouped entirely by the three marks the " +
                "thousands arm reads is vetoed by its shape rather than by its punctuation. Deciding the " +
                "question arm by arm is how two tables came to disagree eleven lines apart, so this test " +
                "compares the arms against each other instead of naming what either holds. These marks are " +
                "read by one arm and not the other: " +
                disagreeing.joinToString(", ") { "U+%04X".format(it.code) },
        )
    }

    @Test
    fun `a separator is the same separator however it is padded with spaces`() {
        val disagreeing =
            MARKS_A_NUMBER_MAY_BE_WRITTEN_WITH.flatMap { mark ->
                val bare = isFound(spelledOneDigitTo(listOf(mark.toString())))
                PADDINGS_A_SEPARATOR_MAY_CARRY
                    .map { padding -> padding(mark) }
                    .filter { padded -> isFound(spelledOneDigitTo(listOf(padded))) != bare }
                    .map { padded -> spelling(listOf(padded)) }
            }
        assertEquals(
            emptyList(),
            disagreeing,
            "a mark and a space are two code points, and the rule that reads one mark between two digits " +
                "counted code points, so every one of thirty-two marks was refused bare and accepted with a " +
                "space after it. The rule counts marks and lets spaces pad them, so these spellings must all " +
                "be read the way the bare mark is: " + disagreeing.joinToString(", "),
        )
    }

    @Test
    fun `a run spelled with more than one separator mark is read as a run spelled with one`() {
        val readSingly =
            MARKS_A_NUMBER_MAY_BE_WRITTEN_WITH
                .filterNot { it.category == CharCategory.SPACE_SEPARATOR }
                .filter { isFound(spelledOneDigitTo(listOf(it.toString()))) }
        val disagreeing =
            readSingly.flatMap { first ->
                readSingly
                    .filterNot { it == first }
                    .map { second -> listOf(first.toString(), second.toString()) }
                    .filterNot { isFound(spelledOneDigitTo(it)) }
                    .map { spelling(it) }
            }
        assertEquals(
            emptyList(),
            disagreeing,
            "the arm that reads a number spread one digit to a separator required one spelling throughout, " +
                "which is the assumption the code makes about its own input rather than a property of a phone " +
                "number, so a run two keystrokes from a pinned refusal was stored. The pairs are drawn from " +
                "the marks this same guard reads singly rather than listed, so a mark cannot be closed here " +
                "and left open there. These mixed spellings are not read: " + disagreeing.joinToString(", ") +
                ". What this sweep does not reach is a spelling that mixes a space with a mark: the spaces in " +
                "a run group it, so those are the case below",
        )
    }

    @Test
    fun `a separator holding two marks is read as one holding one`() {
        val readSingly =
            MARKS_A_NUMBER_MAY_BE_WRITTEN_WITH.filter { isFound(spelledOneDigitTo(listOf(it.toString()))) }
        val disagreeing =
            readSingly.flatMap { first ->
                readSingly
                    .map { second -> listOf("$first$second") }
                    .filterNot { isFound(spelledOneDigitTo(it)) }
                    .map { spelling(it) }
            }
        assertEquals(
            emptyList(),
            disagreeing,
            "how many marks one separator holds is decided once for every arm, at one to eight, so the arm " +
                "that reads a run spread one digit to a separator must not decide it again. It did: it counted " +
                "the marks in each separator and refused more than one, so 9--1--7--5--5--5--0--1--9--9 was " +
                "stored while 9-1-7-5-5-5-0-1-9-9 was refused. The pairs are drawn from the marks this same " +
                "guard reads singly rather than listed. These two-mark separators are not read: " +
                disagreeing.joinToString(", "),
        )
    }

    @Test
    fun `a run spread one digit to a separator is a list of numbers when a list mark ends each number`() {
        listOf(
            "the drill bits I need are 1.5, 2.5, 3.5, 4.5, 5.5 mm for the bracket",
            "I need 1/2, 3/4, 3/8, 5/8, 7/8 fittings for the bathroom job please",
            "torque them to 1.2, 1.4, 1.6, 1.8, 2.0 Nm in sequence",
            "0.5, 1.0, 1.5, 2.0, 2.5, 3.0 mm shims are what I have on hand",
            "sizes 1,5; 2,5; 3,5; 4,5; 5,5 mm",
            "the firmware went 1.2.3, 1.2.4, 1.3.0, 2.0.1 before the boiler died",
            "concrete mix ratios 1:2:4, 1:3:6, 1:1:2, 1:2:3 for the slab",
            "the dip switches are set 1, 0, 1, 1, 0, 0, 1, 0, 1, 1 on the board",
            "2x4, 2x6, 4x4, 6x6, 2x8 lumber for the deck frame",
            "I need 1,500; 2,300; 3,100 of them for the three floors",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `the list veto on the spread arm asks for no cue, because the cue is all that separates the two`() {
        assertPhoneNumber("9, 1, 7, 5, 5, 5, 0, 1, 9, 9 is my cell, please ring about the radiator")
        assertNothingFound("the dip switches are set 1, 0, 1, 1, 0, 0, 1, 0, 1, 1 on the board")
        assertEquals(
            DIALABLE_DIGITS.length,
            "1011001011".length,
            "these two runs are digit for digit the same shape and the same length, one is a phone number and " +
                "one is a row of dip switches, and no property of the digits or of the punctuation separates " +
                "them. What separates them is that the first says ring and the second says switches, so the " +
                "list veto this arm now reads is conditioned on there being no cue nearby rather than on " +
                "anything about the run. A cued list of quantities spread one digit to a comma is therefore " +
                "still refused, and that residue is a product judgement rather than a defect in this rule",
        )
    }

    @Test
    fun `the tail the run trims is one digit wide, and both sides of that boundary are chosen`() {
        assertPhoneNumber("917-555-0199-1")
        assertNothingFound("917-555-0199-01")
        assertPhoneNumber("1-917-555-0199")
        assertNothingFound("01-917-555-0199")
        assertPhoneNumber("0141-445-2266-1 is my number")
        assertNothingFound("0141-445-2266-01 is my number")
        assertEquals(
            listOf(true, false, true, false),
            listOf(
                isFound("917-555-0199-1"),
                isFound("917-555-0199-01"),
                isFound("1-917-555-0199"),
                isFound("01-917-555-0199"),
            ),
            "the run trims single-digit padding from each end and asks again whether what is left is dialable, " +
                "so a one-digit tail trims and a two-digit tail does not, at either end. The boundary is one " +
                "character wide and it is a choice rather than an accident: trimming a two-digit tail would " +
                "make every dialable run followed by a two-digit quantity into a phone number, and a stray " +
                "single digit beside a number is a typo or a list index rather than part of it. Both sides of " +
                "both ends are pinned here, so moving the trim moves this test. 0141 445 2266 is a real " +
                "Glasgow number and its bare four-three-four grouping reaches nothing without a cue, which is " +
                "why the pair above that uses it carries one",
        )
    }

    @Test
    fun `a cued dialable number broken up by a list mark is caught, which is the conjunct this arm rests on`() {
        assertPhoneNumber("ring me on 123456,789, 555 for updates")
        assertNothingFound("quotes came in at 1,200, 1,800, 2,400 and none included parts")
    }

    @Test
    fun `spaces in a mixed spelling group the run, and a grouping no number uses stays an open gap`() {
        assertPhoneNumber("9,1,7 5,5,5 0,1,9,9 is my cell, please ring about the radiator")
        assertPhoneNumber("9.1.7 5.5.5 0.1.9.9 is my cell, please ring about the radiator")
        assertPhoneNumber("9-1-7 5-5-5 0-1-9-9 is my cell, please ring about the radiator")
        assertNothingFound("9-1 7-5 5-5 0-1 9-9 is my cell, please ring about the radiator")
        assertNothingFound("call me when the 1/2 3/4 3/8 5/8 7/8 fittings arrive")
        assertNothingFound("fittings \u00BD \u00BE \u215C \u215D \u215E needed here")
    }

    @Test
    fun `a cue enables a grouped run at ten digits and not at nine, which is a product trade`() {
        listOf(
            "call me when the 300 600 900 mm boards arrive please",
            "the ring main sockets are 100 150 200 cm from the floor",
            "each battery cell reads 100 150 200 on the tester",
            "the label text reads 100 150 200 on the side of the pump",
            "call 10001-1234 now",
            "Water pours through the ceiling below, my cell is 417 555 144",
        ).forEach(::assertNothingFound)
        listOf(
            "call me on 9175550199 about the leak",
            "my cell is 917 555 0199 please ring",
            "ring me on 020 7946 0958 tomorrow morning",
            "three radiators, 600 1200 1800 mm, call ahead please",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `a cue further from the run than the window is not a cue, so the window is pinned from above too`() {
        listOf(
            "the flats on this floor are 1010 1020 1030 and the caretaker will ring you back later",
            "the meter in the hall reads 1000 1500 2000 which the landlord asked me to check before you call",
        ).forEach(::assertNothingFound)
        assertPhoneNumber("the caretaker will ring you back on 1010 1020 1030 later")
    }

    @Test
    fun `a cued run grouped like thousands is money however many digits it holds`() {
        listOf(
            "call me about the 1,250,000,000 lira bill",
            "call me about the 123,456,789,012 lira bill",
            "call me about the 1;250;000;000 lira bill",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `a separator is anything that is neither a letter nor a digit`() {
        listOf(
            "917\u3002555\u30020199",
            "917\uFF61555\uFF610199",
            "917*555*0199",
            "917|555|0199",
            "917~555~0199",
            "917#555#0199",
            "917'555'0199",
            "917=555=0199",
            "917>555>0199",
            "917!555!0199",
            "917?555?0199",
            "917&555&0199",
            "917+555+0199",
            "917@555@0199",
            "Water pours through the ceiling below, my cell is 917*555*0199",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `the separator rule reaches one false positive class the space separated forms already had`() {
        listOf(
            "we paid 12 3450 6789 in total for the whole bathroom job",
            "we paid 12 + 3450 + 6789 in total for the whole bathroom job",
            "the pipe run is 917\u2044555\u20440199 mm of copper",
        ).forEach(::assertPhoneNumber)
        listOf(
            "the meter reads 100 150 200 across the three gauges",
            "the meter reads 100 150 200 across the three dials",
            "the meter reads 100 = 150 = 200 across the three dials",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `a ten digit number is found whether its trunk digit joins the area code or the exchange`() {
        listOf(
            "Leaking tap under the sink, reach me 91-7555-0199 anytime",
            "Boiler wont fire, hit me up 21 2555 1234 before noon please",
            "91-7555-0199",
            "21 2555 1234",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `the phone cue arm is a list, and these ways of asking to be rung are not in it`() {
        listOf(
            "reach me on 9175550199",
            "hit me up on 9175550199",
            "buzz me on 9175550199",
            "dm me on 9175550199",
            "beep me on 9175550199",
            "give me a bell on 9175550199",
            "9175550199 is my landline",
            "9175550199 is my home line",
            "9175550199 is my work line",
        ).forEach(::assertNothingFound)
        assertPhoneNumber("9175550199 is my mobile")
    }

    @Test
    fun `a cued run whose last group is shorter than a line number is a quantity list`() {
        assertNothingFound("call me about the 60 40 30 20 10 split")
        assertNothingFound("ring me about the 12 15 18 21 24 spacings")
    }

    @Test
    fun `these cued dialable numbers are a known cost of that floor and not a specification`() {
        listOf(
            "my number is 917 555 01 99 if you need it",
            "call me on 917 555 01 99 about the leak",
            "call me on 91 7555 01 99 about the leak",
            "call me on 12 34 56 78 90",
            "text me on 9175 5501 99 today",
            "phone me on 917-555-01-99 tomorrow",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `a cue asks for a count of digits and not for a group narrow enough to be a phone group`() {
        listOf(
            "call me on 917 5550199 about the leak",
            "call me on 212 5551234 about the leak",
            "call me on 2125551 234 about the leak",
            "Kitchen tap drips from the base, text me at 917 5550199 to arrange a look",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `the two limits a cue does not lower are how many groups there are and how long the last one is`() {
        assertNothingFound("call me about the 60 40 30 20 10 split")
        assertNothingFound("call me when the 1/2 3/4 3/8 5/8 7/8 fittings arrive")
        assertEquals(
            listOf(true, false, false),
            listOf(
                isFound("call me on 917 5550199 about the leak"),
                isFound("call me on 91 75 55 01 99 12 345 about the leak"),
                isFound("call me on 917 555 019 90 about the leak"),
            ),
            "ADR-0007 says a cue lowers the shape requirement and asks only for a count of digits, and the " +
                "arm read the whole grouping limit before the count, so a real number typed without one space " +
                "was stored: 917 5550199 beside a cue is ten digits in two groups and its second group is " +
                "seven digits long, one over what a phone group may hold. A cue no longer lowers how many " +
                "groups a number may have, because eight groups of one digit beside a cue is the quantity " +
                "list this arm exists not to refuse, and it no longer lowers how long the last group must be, " +
                "because a two-digit tail is what a spacing list ends with. Those two are what is left of the " +
                "limit under a cue, and both are measured here rather than described",
        )
    }

    @Test
    fun `these ten digit dialable numbers are not found, and the gap is a grouping the rule does not read`() {
        listOf(
            "2125-551234",
            "212-5551234",
            "2125.551234",
            "2125551-234",
            "2125.551.234",
            "91755 50199",
            "44-7700-900123",
            "44 7700 900123",
            "0044 20 7946 0958",
            "reach 1 2125 5512 34",
            "917 555 01 99",
            "917,555,01,99",
            "(917) 555 01 99",
            "9175 5501 99",
            "917-555-01-99",
            "Kitchen tap drips from the base, reach me on 917 555 01 99 to arrange a look",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `an uncued dialable run means exactly ten digits, so no eleven digit number is found at all`() {
        listOf(
            "020 7946 0958",
            "0207 946 0958",
            "07700 900123",
            "07700 900 123",
            "0113 496 0123",
            "0161 496 0123",
            "030 0123 4567",
            "1917 555 0199",
            "001 212 555 1234",
            "011 1 212 555 1234",
            "Water pours through the ceiling below, 020 7946 0958 is my landline",
            "The ceiling is leaking badly, 1917 555 0199 if you want a look",
        ).forEach(::assertNothingFound)
        listOf(
            "Water pours through the ceiling below, 07700 900 123 is my number",
            "Water pours through the ceiling below, 1917 555 0199 is my cell",
            "Water pours through the ceiling below, 001 212 555 1234 is my cell",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `the uncued rule was measured at eleven digits and refused, and both sides of that are pinned`() {
        assertNothingFound("Radiator sizes are 600 1200 1800 mm across the flat here")
        assertNothingFound("020 7946 0958")
    }

    @Test
    fun `a stray single digit inside a ten digit run does not hide the number`() {
        assertPhoneNumber("917 555 019 9")
        assertPhoneNumber("9 175 550 199")
        assertPhoneNumber("917-555-019-9")
        assertNothingFound("the runs are 120 150 180 mm and 1 more to come")
    }

    @Test
    fun `that arm rejects four sentences written with a space for a thousands separator, and that is the cost`() {
        listOf(
            "the quote came to 1 250 000 000 lira for the whole block here",
            "We counted 8 200 300 400 mm lengths of skirting in the hall here",
            "I need 2 600 900 1200 mm boards cut for the shelves in the alcove",
            "The riser needs 6 100 150 200 mm couplers to finish the run here",
        ).forEach(::assertPhoneNumber)
        assertNothingFound("boiler serial 1234567890 is on the plate behind the panel")
        assertNothingFound("Worktop depths 300 600 900 mm and the sink is undermount")
    }

    @Test
    fun `a run the surrounding words name as something else is not a dialable run broken up by stray digits`() {
        listOf(
            "boiler serial 1 234 567 890 is on the plate behind the panel",
            "the meter reading was 1 234 567 890 when I looked this morning",
            "Radiator widths in the hall are 100 200 300 3 mm across the flat",
        ).forEach(::assertNothingFound)
        assertPhoneNumber("the quote came to 1 250 000 000 lira for the whole block here")
    }

    @Test
    fun `an SI grouped quantity and a dialable run with no words around either are read the same way`() {
        assertPhoneNumber("1 234 567 890")
        assertPhoneNumber("9 175 550 199")
    }

    @Test
    fun `a cued run grouped like thousands is money and not a phone number`() {
        assertNothingFound("call me about the 123,456,789 lira bill")
        assertNothingFound("call me about the 1,250,000 lira bill")
        assertPhoneNumber("call me on 917,555,0199")
    }

    @Test
    fun `an uncued run not grouped as an exchange and a line is a list of quantities`() {
        listOf(
            "the quotes were 1200, 450, 600 from three firms",
            "the runs are 1500, 900, 750 mm end to end here",
            "1234,567,890 is best",
            "readings were 120 130 125 psi over three days",
            "meter readings:\n120\n130\n125\nover three days",
            "quotes so far:\n1200\n450\n600\nfrom three firms",
            "flats 101 102 103 all have the same leak",
            "codes 101-102-103 are on the fuse box door",
            "rooms 201 305 410 are affected by the damp",
            "phase readings 230 231 229 volts at the board",
            "Radiator sizes are 600 1200 1800 mm across the flat here",
            "Worktop depths 300 600 900 mm and the sink is undermount",
            "Tile sizes 200 300 400 600 needed for the bathroom floor",
            "Loft insulation 100 200 270 mm depths quoted by the last",
            "Skirting lengths 240 300 360 cm needed across three rooms",
            "Previous quotes were 120 150 180 for the same tap repair",
            "Previous quotes were 1000, 300, 600 for the same repair",
            "Apartment 3B, 250 sq ft, quotes 120 150 180 for the work",
            "Radiator widths:\n300\n450\n600",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `each condition the uncued rule short circuits on has an input that reaches it`() {
        listOf(
            "the plate reads 1234 567 890 under the serial number here",
            "the plate reads 12345 6 7890 under the serial number here",
            "the plate reads 1 4444 1 4444 under the serial number here",
            "the plate reads 1 4444 44 44 under the serial number here",
            "the plate reads 12345 67890 under the serial number here",
            "the plate reads 1 55555 4444 under the serial number here",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `a thousands separated list of prices is money under a cue as well as without one`() {
        val lists =
            listOf(
                "rated 10,000, 12,000 or 14,000 BTU",
                "quotes were 1,500, 2,300, 3,100",
                "12,000, 18,000",
                "1,000, 2,500, 10,000",
                "1,500; 2,300; 3,100",
                "1,200, 1,800, 2,400",
                "12,000, 18,000, 24,000",
                "120,000, 180,000",
                "sizes 100, 150, 200 mm",
                "boiler serviced 2019, 2021, 2023 and now it leaks",
            )
        lists.forEach(::assertNothingFound)
        lists.map { "$it, call me anytime" }.forEach(::assertNothingFound)
        assertPhoneNumber("call me on 917, 555, 0199 about the leak")
        assertPhoneNumber("call me on 917,555,0199 about the leak")
    }

    @Test
    fun `the cue arm still reads a list whose numbers are separated by a space alone, and that is the price`() {
        listOf(
            "boiler serviced 2019 2021 2023, call me",
            "1400 1600 1800 mm, call me",
            "the invoices are 4455 4456 4457, call me about the three visits",
            "10,000 12,000 14,000 BTU, call me",
            "2026-09-24, 350 Example Street, call me",
            "\u00A312,000, \u00A318,000, call me",
        ).forEach(::assertPhoneNumber)
        assertNothingFound("boiler serviced 2019 2021 2023")
        assertNothingFound("1400 1600 1800 mm")
    }

    @Test
    fun `this is a known false positive and not a specification, a quantity list grouped three three four`() {
        assertPhoneNumber("100, 150, 2000 mm")
        assertPhoneNumber("the runs are 100 150 2000 mm end to end")
    }

    @Test
    fun `an uncued run whose every group is longer or shorter than an exchange is not a phone number`() {
        assertNothingFound("boiler serviced 2019, 2021, 2023 and now it leaks")
        assertNothingFound("boiler serviced 2019 2021 2023 and now it leaks")
        assertNothingFound("the radiators are 1400, 1600, 1800 mm along that wall")
        assertNothingFound("the radiators are 1400 1600 1800 mm along that wall")
        assertNothingFound("radiator widths are\n1400\n1600\n1800\nacross the hallway")
        assertNothingFound("invoices 4455 4456 4457 are all still unpaid")
        assertNothingFound("lengths 1200 1500 1800 and 2100 mm are needed")
        assertNothingFound("the gauge showed 12 34 56 78 90 across the week")
    }

    @Test
    fun `a grouped run with no group long enough for an exchange is not a phone number`() {
        assertNothingFound("the gauge showed 12 34 56 78 90 across the week")
    }

    @Test
    fun `finds nothing in the numbers a real description carries`() {
        listOf(
            "boiler serial 1234567890",
            "serial number 1234567890 is on the plate behind the panel",
            "the part number is 0141-445-2266-01 on the label",
            "appliance model ecoTEC plus 832, serial 21123400123456789",
            "apartment 4B, buzzer 12 is on the left of the door",
            "the meter reading was 98765 on 2026-09-24",
            "invoice 4455 dated 2026-09-24 covers 3 visits",
            "the parts cost 12.50 13.75 14.00 all in",
            "sizes 10 12 14 16 18 20 22 are all wrong",
            "replace washers 1 2 3 4 5 6 7 8 9 10 in that order",
            "the pipe is 3\u00BD inch across the joint",
            "the flue is 600 mm long and the gap is 870 mm",
            "a 2015 model that has leaked for 14 days",
            "350 Example Street, apartment 4B, third floor walk up",
            "the manual is at vaillant.co.uk if you want to read it",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `a ten digit run with no grouping and no phone cue is not a phone number`() {
        assertNothingFound("boiler serial 1234567890")
        assertNothingFound("the plate reads 9175550199 and nothing else")
    }

    @Test
    fun `a nine digit run is not read as a phone number`() {
        assertNothingFound("serial 917555019")
    }

    @Test
    fun `an eleven digit run adjacent to more digits is not read as a phone number`() {
        assertNothingFound("serial 191755501990001")
    }

    @Test
    fun `a group longer than any phone group is not part of a phone number`() {
        assertNothingFound("the coil is stamped 1234567 890 on the side")
    }
}
