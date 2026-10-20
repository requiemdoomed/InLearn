package com.inlearn.ui.support;

import com.inlearn.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateTimeInputsTest {

    @Test
    void acceptsCommonTimeFormats() {
        assertEquals(LocalTime.of(23, 59), DateTimeInputs.parseTime("23:59", "due time"));
        assertEquals(LocalTime.of(9, 5), DateTimeInputs.parseTime("9:05", "due time"));
        assertEquals(LocalTime.of(23, 59), DateTimeInputs.parseTime("11:59 pm", "due time"));
        assertEquals(LocalTime.of(17, 0), DateTimeInputs.parseTime("5PM", "due time"));
        assertThrows(ValidationException.class, () -> DateTimeInputs.parseTime("25:00", "due time"));
    }

    @Test
    void deadlinesAreInterpretedInTheDisplayZoneAndStoredAsInstants() {
        Instant due = DateTimeInputs.toInstant(LocalDate.of(2026, 10, 22), "23:59", ZoneId.of("America/Toronto"), "due");
        assertEquals(Instant.parse("2026-10-23T03:59:00Z"), due);
    }
}
