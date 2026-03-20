package com.mmo.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TimeUtil {

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
}
