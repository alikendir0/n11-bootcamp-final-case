package com.n11.order.order;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Order-service-specific advice that maps {@link OrderService.PriceDriftException}
 * to HTTP 409 RFC-7807 with a custom {@code updatedItems[]} property. Runs at
 * {@link Ordered#HIGHEST_PRECEDENCE} so it wins over the generic
 * {@code common-error.ProblemDetailControllerAdvice}'s broad
 * {@code ExceptionHandler(Exception.class)} branch.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderExceptionHandler {

    private static final URI PRICE_DRIFT_TYPE = URI.create("https://n11clone/errors/price-drift");

    @ExceptionHandler(OrderService.PriceDriftException.class)
    @ResponseBody
    public ProblemDetail handlePriceDrift(OrderService.PriceDriftException ex,
                                          HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setType(PRICE_DRIFT_TYPE);
        pd.setTitle("Price drift detected");
        pd.setDetail(ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));
        String cid = MDC.get("correlationId");
        pd.setProperty("correlationId", cid == null ? "unknown" : cid);
        List<Map<String, Object>> updated = ex.getUpdatedItems().stream()
            .map(u -> Map.<String, Object>of(
                "productId",    u.productId(),
                "oldUnitPrice", u.snapshotPrice(),
                "newUnitPrice", u.currentPrice()))
            .toList();
        pd.setProperty("updatedItems", updated);
        return pd;
    }
}
