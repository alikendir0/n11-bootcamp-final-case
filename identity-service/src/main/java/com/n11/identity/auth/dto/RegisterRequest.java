package com.n11.identity.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Register request body. Validation messages in Turkish per LOC requirement
 * (CLAUDE.md: "Frontend in Turkish; identifiers in English").
 *
 * Password rule (CD-01 floor): >= 8 chars, at least one letter and one digit.
 */
public record RegisterRequest(
        @NotBlank(message = "E-posta adresi zorunludur")
        @Email(message = "Geçerli bir e-posta adresi giriniz")
        String email,

        @NotBlank(message = "Şifre zorunludur")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "Şifre en az 8 karakter, bir harf ve bir rakam içermelidir"
        )
        String password,

        @NotBlank(message = "Ad Soyad zorunludur")
        String fullName
) { }
