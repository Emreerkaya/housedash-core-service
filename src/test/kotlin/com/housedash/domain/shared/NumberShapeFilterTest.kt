package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NumberShapeFilterTest {
    private fun assertPhoneNumber(text: String) =
        assertEquals(
            setOf(ContactDetail.PhoneNumber),
            contactDetailsIn(text),
            text,
        )

    private fun assertNothingFound(text: String) = assertEquals(emptySet(), contactDetailsIn(text), text)

    @Test
    fun `a stray single digit inside a ten digit run does not hide the number`() {
        assertPhoneNumber("917 555 019 9")
        assertPhoneNumber("9 175 550 199")
        assertPhoneNumber("917-555-019-9")
        assertNothingFound("the runs are 120 150 180 mm and 1 more to come")
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
            "350 East 62nd Street, apartment 4B, third floor walk up",
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
