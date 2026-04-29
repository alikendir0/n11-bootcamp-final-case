package com.n11.identity.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Address creation body. Türkiye-only fields per D-09. Validation messages in Turkish.
 */
public record CreateAddressRequest(
        @NotBlank(message = "Adres başlığı zorunludur")
        @Size(max = 50, message = "Adres başlığı en fazla 50 karakter olabilir")
        String title,

        @NotBlank(message = "Alıcı Ad Soyad zorunludur")
        @Size(max = 120, message = "Alıcı Ad Soyad en fazla 120 karakter olabilir")
        String recipientName,

        @NotBlank(message = "Telefon zorunludur")
        @Size(max = 20, message = "Telefon en fazla 20 karakter olabilir")
        String phone,

        @NotBlank(message = "İl zorunludur")
        @Size(max = 50, message = "İl en fazla 50 karakter olabilir")
        String il,

        @NotBlank(message = "İlçe zorunludur")
        @Size(max = 80, message = "İlçe en fazla 80 karakter olabilir")
        String ilce,

        @Size(max = 120, message = "Mahalle en fazla 120 karakter olabilir")
        String mahalle,

        @NotBlank(message = "Sokak/Cadde/No/Daire zorunludur")
        @Size(max = 255, message = "Adres satırı en fazla 255 karakter olabilir")
        String streetLine,

        @NotBlank(message = "Posta kodu zorunludur")
        @Pattern(regexp = "^\\d{5}$", message = "Posta kodu 5 haneli rakam olmalıdır")
        String postalCode,

        boolean isDefault
) { }
