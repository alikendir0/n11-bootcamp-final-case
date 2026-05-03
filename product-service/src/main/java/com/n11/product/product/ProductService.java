package com.n11.product.product;

import com.n11.product.category.Category;
import com.n11.product.category.CategoryRepository;
import com.n11.product.product.dto.CreateProductRequest;
import com.n11.product.product.dto.ProductDetailDto;
import com.n11.product.product.dto.ProductSummaryDto;
import com.n11.product.product.outbox.ProductOutboxPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductService {

    private static final BigDecimal DEFAULT_KDV_RATE = new BigDecimal("20.00");
    private static final String DEFAULT_SELLER_NAME = "n11 Pazaryeri";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductOutboxPublisher outboxPublisher;
    private final SearchServiceClient searchServiceClient;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          ProductOutboxPublisher outboxPublisher,
                          SearchServiceClient searchServiceClient) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.outboxPublisher = outboxPublisher;
        this.searchServiceClient = searchServiceClient;
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDto> search(String q, UUID categoryId, Pageable pageable) {
        String catParam = categoryId != null ? categoryId.toString() : null;
        Page<Product> page = null;

        if (q != null && !q.isBlank()) {
            int limit = Math.max(50, (pageable.getPageNumber() + 1) * pageable.getPageSize());
            List<UUID> uuids = searchServiceClient.search(q, limit);
            if (uuids != null && !uuids.isEmpty()) {
                page = productRepository.findByIdsAndCategory(uuids, catParam, pageable);
            } else if (uuids != null) {
                // Semantic search succeeded but returned empty list
                return Page.empty(pageable);
            }
        }

        if (page == null) {
            // Fallback to text search if no query, or semantic search failed
            page = productRepository.search(q, catParam, pageable);
        }
        
        return page.map(ProductService::toSummary);
    }

    @Transactional(readOnly = true)
    public ProductDetailDto getDetail(UUID id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        return toDetail(p);
    }

    @Transactional
    public ProductDetailDto create(CreateProductRequest req) {
        if (productRepository.existsBySku(req.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU zaten mevcut");
        }
        Category category = null;
        if (req.categoryId() != null) {
            category = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategori bulunamadı"));
        }
        BigDecimal kdvRate = req.kdvRate() != null ? req.kdvRate() : DEFAULT_KDV_RATE;
        String sellerName = (req.sellerName() != null && !req.sellerName().isBlank())
                ? req.sellerName() : DEFAULT_SELLER_NAME;
        String[] images = req.imageUrls() != null
                ? req.imageUrls().toArray(new String[0]) : new String[0];

        UUID id = UUID.randomUUID();
        String slug = toSlug(req.nameTr()) + "-" + id.toString().substring(0, 8);
        Instant now = Instant.now();

        Product p = new Product(id, req.sku(), req.nameTr(), req.descriptionTr(),
                req.priceGross(), kdvRate, category, images, sellerName, slug, now, now);
        productRepository.save(p);
        outboxPublisher.publishCreated(p);
        return toDetail(p);
    }

    @Transactional
    public ProductDetailDto update(UUID id, CreateProductRequest req) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        p.rename(req.nameTr());
        p.setDescription(req.descriptionTr());
        BigDecimal kdvRate = req.kdvRate() != null ? req.kdvRate() : p.getKdvRate();
        p.reprice(req.priceGross(), kdvRate);
        if (req.imageUrls() != null) {
            p.setImageUrls(req.imageUrls().toArray(new String[0]));
        }
        if (req.categoryId() != null) {
            Category category = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategori bulunamadı"));
            p.setCategory(category);
        }
        if (req.sellerName() != null && !req.sellerName().isBlank()) {
            p.setSellerName(req.sellerName());
        }
        productRepository.save(p);
        outboxPublisher.publishUpdated(p);
        return toDetail(p);
    }

    @Transactional
    public void delete(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı");
        }
        productRepository.deleteById(id);
        outboxPublisher.publishDeleted(id);
    }

    // --- mappers ---

    private static ProductSummaryDto toSummary(Product p) {
        String firstImg = (p.getImageUrls() != null && p.getImageUrls().length > 0)
                ? p.getImageUrls()[0] : null;
        String catName = p.getCategory() != null ? p.getCategory().getNameTr() : null;
        return new ProductSummaryDto(
                p.getId(), p.getSku(), p.getNameTr(),
                p.getPriceGross(), p.getKdvRate(),
                p.getSlug(), catName, firstImg);
    }

    private static ProductDetailDto toDetail(Product p) {
        List<String> images = p.getImageUrls() != null ? List.of(p.getImageUrls()) : List.of();
        UUID catId = p.getCategory() != null ? p.getCategory().getId() : null;
        String catName = p.getCategory() != null ? p.getCategory().getNameTr() : null;
        return new ProductDetailDto(
                p.getId(), p.getSku(), p.getNameTr(), p.getDescriptionTr(),
                p.getPriceGross(), p.getKdvRate(),
                p.getSlug(), p.getSellerName(),
                images, catId, catName);
    }

    // Turkish-aware slug: tr-TR locale lower-case + ASCII-fold + dash collapse.
    static String toSlug(String nameTr) {
        String s = nameTr.toLowerCase(new Locale("tr", "TR"));
        // Replace common Turkish characters with ASCII equivalents:
        s = s.replace("ı", "i").replace("ğ", "g").replace("ü", "u")
             .replace("ş", "s").replace("ö", "o").replace("ç", "c");
        s = s.replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("-{2,}", "-");
        s = s.replaceAll("^-|-$", "");
        return s.isEmpty() ? "urun" : s;
    }
}
