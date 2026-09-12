package com.example.displayconnect.routing

import org.junit.Assert.*
import org.junit.Test

class AddressQueryTest {
    @Test fun expandsPortugueseStreetPrefixesAndKeepsCity() {
        assertEquals("Avenida Paulista, 1000, São Paulo, SP", AddressQuery.withCity("  Av.  Paulista, 1000  ", "São Paulo, SP"))
        assertEquals("Rua das Flores", AddressQuery.normalize("R. das Flores"))
        assertEquals("Travessa Central", AddressQuery.normalize("Tv Central"))
    }
    @Test fun streetDatesAndHighwayNumbersArePreserved() {
        listOf("Rua 25 de Março", "Avenida 9 de Julho", "BR-101", "Rua 7, Centro").forEach {
            assertEquals(it, AddressQuery.withoutHouseNumber(it))
            assertNull(AddressQuery.requestedHouseNumber(it))
        }
    }
    @Test fun onlyExplicitHouseNumberIsRemovedForFallback() {
        assertEquals("120", AddressQuery.requestedHouseNumber("Rua 25 de Março, 120, São Paulo"))
        assertEquals("Rua 25 de Março, São Paulo", AddressQuery.withoutHouseNumber("Rua 25 de Março, 120, São Paulo"))
        assertEquals("Rua das Flores", AddressQuery.withoutHouseNumber("Rua das Flores nº 12"))
    }
}
