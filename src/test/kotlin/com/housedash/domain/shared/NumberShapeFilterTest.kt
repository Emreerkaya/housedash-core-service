package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private val DIGIT_GROUPS = Regex("""\p{Nd}++""")

private fun digitGroupsOf(text: String): List<Int> = DIGIT_GROUPS.findAll(text).map { it.value.length }.toList()

private val DIGIT_GROUPS_OF_A_UK_LANDLINE = digitGroupsOf("020 7946 0958")

class NumberShapeFilterTest {
    @Test
    fun `a separator is anything but a letter, a digit and the three marks the thousands arm owns`() {
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
    fun `the separator rule reaches two false positive classes the space separated forms already had`() {
        listOf(
            "the meter reads 100 150 200 across the three dials",
            "the meter reads 100 = 150 = 200 across the three dials",
            "we paid 12 3450 6789 in total for the whole bathroom job",
            "we paid 12 + 3450 + 6789 in total for the whole bathroom job",
            "the pipe run is 917\u2044555\u20440199 mm of copper",
        ).forEach(::assertPhoneNumber)
        assertNothingFound("the meter reads 100 150 200 across the three gauges")
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
        ).forEach(::assertNothingFound)
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
            "call me on 12 34 56 78 90",
            "text me on 9175 5501 99 today",
            "phone me on 917-555-01-99 tomorrow",
        ).forEach(::assertNothingFound)
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
    fun `extending the uncued rule to eleven digits was measured and refused`() {
        assertEquals(
            DIGIT_GROUPS_OF_A_UK_LANDLINE,
            digitGroupsOf("Radiator sizes are 600 1200 1800 mm across the flat here"),
            "a UK landline written 020 7946 0958 and a radiator size list written 600 1200 1800 have the same " +
                "group widths and the same total, so a rule that reads one reads the other. Extending the " +
                "uncued arm from exactly ten digits to ten or eleven was run: it gains the UK landline row and " +
                "loses this sentence, which the previous round closed, and reddens the quantity-list pin below. " +
                "One row each way, and a false positive costs more than a false negative, so the eleven digit " +
                "class stays open and is declared above rather than closed",
        )
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
    fun `that arm rejects a number written with a space for its thousands separator, and that is a cost`() {
        listOf(
            "boiler serial 1 234 567 890 is on the plate behind the panel",
            "the meter reading was 1 234 567 890 when I looked this morning",
            "the quote came to 1 250 000 000 lira for the whole block here",
            "We counted 8 200 300 400 mm lengths of skirting in the hall here",
            "I need 2 600 900 1200 mm boards cut for the shelves in the alcove",
            "The riser needs 6 100 150 200 mm couplers to finish the run here",
            "Radiator widths in the hall are 100 200 300 3 mm across the flat",
        ).forEach(::assertPhoneNumber)
        assertNothingFound("boiler serial 1234567890 is on the plate behind the panel")
        assertNothingFound("Worktop depths 300 600 900 mm and the sink is undermount")
    }

    @Test
    fun `nothing in the shape of a number separates an SI grouped quantity from a dialable run`() {
        val serial = "1 234 567 890"
        val dialable = "9 175 550 199"
        assertEquals(
            digitGroupsOf(serial),
            digitGroupsOf(dialable),
            "the SI-separated serial above and the dialable number beside it are pinned as behaving the same " +
                "way because nothing distinguishes them by shape: same group widths, same separators, same " +
                "total. If this assertion ever fails, a shape-based narrowing has become possible and the cost " +
                "pinned above stops being unavoidable",
        )
        assertPhoneNumber(serial)
        assertPhoneNumber(dialable)
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
    fun `a thousands separated list of prices is money whatever its groups alternate to`() {
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
        ).forEach(::assertNothingFound)
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
