package com.codesync.execution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionMessage {
    private Long executionId;
    private String code;
    private com.codesync.execution.model.ExecutionLanguage language;
    private String stdin;
}
