package com.steamfolder.steamfolder.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean success;
    private final String code;
    private final String message;
    private final List<FieldErrorDetail> errors;
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String customMessage) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(customMessage)
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .errors(FieldErrorDetail.of(bindingResult))
                .build();
    }

    @Getter
    @Builder
    public static class FieldErrorDetail {
        private final String field;
        private final String value;
        private final String reason;

        public static List<FieldErrorDetail> of(BindingResult bindingResult) {
            List<FieldError> fieldErrors = bindingResult.getFieldErrors();
            return fieldErrors.stream()
                    .map(error -> FieldErrorDetail.builder()
                            .field(error.getField())
                            .value(error.getRejectedValue() == null ? "" : error.getRejectedValue().toString())
                            .reason(error.getDefaultMessage())
                            .build())
                    .collect(Collectors.toList());
        }
    }
}
