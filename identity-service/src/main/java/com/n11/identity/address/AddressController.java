package com.n11.identity.address;

import com.n11.identity.address.dto.AddressResponse;
import com.n11.identity.address.dto.CreateAddressRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Address book per AUTH-08. Both endpoints rely on the gateway-injected
 * {@code X-User-Id} header (D-15: zero JWT decoding in identity-service).
 */
@RestController
@RequestMapping("/addresses")
public class AddressController {

    private static final String HEADER_USER_ID = "X-User-Id";

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(HttpServletRequest request) {
        UUID userId = resolveUserId(request);
        return addressService.listForUser(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(HttpServletRequest request,
                                   @Valid @RequestBody CreateAddressRequest body) {
        UUID userId = resolveUserId(request);
        return addressService.create(userId, body);
    }

    private static UUID resolveUserId(HttpServletRequest request) {
        String header = request.getHeader(HEADER_USER_ID);
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kimlik doğrulaması gerekli");
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı kimliği");
        }
    }
}
