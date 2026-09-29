package com.housedash.domain.shared

import org.junit.jupiter.api.Test

class WordedAddressFilterTest {
    @Test
    fun `the worded at reads every dot the file declares, and the comma is a measured refusal`() {
        listOf(
            "bob at gmail\u00B7com",
            "bob at gmail\u2022com",
            "bob at gmail\u2027com",
            "bob at gmail\u30FBcom",
            "bob at gmail\u3002com",
            "bob at gmail\uFF61com",
            "bob at gmail {dot} com",
            "bob at gmail <dot> com",
            "bob (at) gmail (dot) com",
            "bob at bobsplumbing {dot} com",
            "bob at bobsplumbing <dot> com",
            "bob {at} bobsplumbing {dot} com",
            "bob <at> bobsplumbing <dot> com",
            "bobby at yahoo\u00B7com about the leak under the kitchen sink here",
        ).forEach(::assertEmailAddress)
        assertNothingFound("bob at gmail,com")
        listOf(
            "the wire at live, us and neutral are both loose in the socket",
            "she was at proton, me first and then came round to look at it",
            "we looked at yahoo, com and could not find the part anywhere",
            "I left the parcel at hotmail, info is what the label said",
            "the tap is at outlook, org of the building is unclear here",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `the comma for a dot arm is restricted to a listed label, and the obvious widening is wrong`() {
        assertNothingFound("write to bob@mail,ru for the photos of the ceiling damage")
        listOf(
            "email me @bob, please have a look at the riser in the hall",
            "the super @bob, please let him in when he arrives tomorrow",
            "price @ two, however the board wants a third quote first",
            "text @sam, before you come round tomorrow about the leak",
        ).forEach(::assertNothingFound)
        assertEmailAddress("the meter @bob, info is on the door of the cupboard here")
    }
}
