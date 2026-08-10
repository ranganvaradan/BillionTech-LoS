package com.los.core.service.kfs.edi;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndianAmountInWordsTest {

    @Test
    void convert_zero() {
        assertEquals("zero", IndianAmountInWords.convert(0));
    }

    @Test
    void convert_typicalLoanAmount() {
        assertEquals("eighty-five thousand", IndianAmountInWords.convert(85_000));
    }
}
