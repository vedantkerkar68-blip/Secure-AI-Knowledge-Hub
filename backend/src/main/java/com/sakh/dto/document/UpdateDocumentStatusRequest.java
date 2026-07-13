package com.sakh.dto.document;

import com.sakh.enums.DocumentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDocumentStatusRequest {

    @NotNull(message = "Status is required")
    private DocumentStatus status;
}