package com.n11.identity.address;

import com.n11.identity.address.dto.AddressResponse;
import com.n11.identity.address.dto.CreateAddressRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listForUser(UUID userId) {
        return addressRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AddressService::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * D-11: at most one is_default=true address per user. If the request marks
     * the new address default, flip any existing default to false in the same
     * transaction BEFORE inserting the new row.
     */
    @Transactional
    public AddressResponse create(UUID userId, CreateAddressRequest req) {
        if (req.isDefault()) {
            addressRepository.clearDefaultForUser(userId);
        }
        Address entity = new Address(
                UUID.randomUUID(),
                userId,
                req.title(),
                req.recipientName(),
                req.phone(),
                req.il(),
                req.ilce(),
                req.mahalle(),
                req.streetLine(),
                req.postalCode(),
                req.isDefault(),
                Instant.now()
        );
        Address saved = addressRepository.save(entity);
        return toResponse(saved);
    }

    private static AddressResponse toResponse(Address a) {
        return new AddressResponse(
                a.getId(),
                a.getTitle(),
                a.getRecipientName(),
                a.getPhone(),
                a.getIl(),
                a.getIlce(),
                a.getMahalle(),
                a.getStreetLine(),
                a.getPostalCode(),
                a.isDefault(),
                a.getCreatedAt()
        );
    }
}
