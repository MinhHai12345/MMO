package com.mmo.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateTimeUtils {

    public static boolean isUpcoming(String time) {
        return time != null && time.contains(":");
    }

    public static LocalDateTime parseTime(String time) {
        try {
            LocalTime t = LocalTime.parse(time);
            return LocalDate.now().atTime(t);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date convertToDate(final ZonedDateTime dateToConvert) {
        return Date.from(dateToConvert.toInstant());
    }

    public static String today() {
        return todayLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public static LocalDate todayLocalDate() {
        return LocalDate.now().plusDays(2);
    }
}
