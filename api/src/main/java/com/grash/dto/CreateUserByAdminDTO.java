package com.grash.dto;

import com.grash.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@Schema(description = "DTO for creating a user manually (admin); the user sets their own password via an emailed link")
public class CreateUserByAdminDTO {
    @NotNull
    private String firstName;
    @NotNull
    private String lastName;
    @NotNull
    private String email;
    private String phone;
    @NotNull
    private Role role;
}
