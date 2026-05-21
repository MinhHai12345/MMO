package com.mmo.cronjob.exception;

public class ScheduleJobException extends RuntimeException {

    /**
     * @param message String
     */
    public ScheduleJobException(final String message) {
        super(message);
    }

    public ScheduleJobException(final String s, final Throwable throwable) {
        super(s, throwable);
    }
}
