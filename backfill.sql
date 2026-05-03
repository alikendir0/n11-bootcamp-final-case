INSERT INTO product.outbox (id, aggregate, event_type, payload, occurred_at, sent_at)
SELECT 
    gen_random_uuid(),
    'products',
    'product.created',
    jsonb_build_object(
        'eventId', gen_random_uuid(),
        'eventType', 'product.created',
        'eventVersion', 1,
        'occurredAt', current_timestamp,
        'correlationId', gen_random_uuid(),
        'producer', 'product-service',
        'payload', jsonb_build_object(
            'productId', id,
            'sku', sku,
            'nameTr', name_tr,
            'descriptionTr', description_tr,
            'priceGross', price_gross,
            'categoryId', category_id
        )
    ),
    current_timestamp,
    NULL
FROM product.products;
