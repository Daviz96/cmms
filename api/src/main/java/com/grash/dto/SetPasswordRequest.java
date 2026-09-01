package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@Schema(description = "Request to set a user's password from a one-time token (welcome / set-password flow)")
public class SetPasswordRequest {
    @NotNull
    private String token;
    @NotNull
    @Size(min = 12, max = 128)
    private String newPassword;
}
