package com.mmo.cronjob.exception;

import lombok.Getter;

@Getter
public class InvalidCronJobException extends RuntimeException {
    private final String value;
    private final String fieldName;

    public InvalidCronJobException(final String value, final String fieldName) {
        this.fieldName = fieldName;
        this.value = value;
    }
}
