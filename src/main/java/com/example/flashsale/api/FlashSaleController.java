package com.example.flashsale.api;

import com.example.flashsale.api.dto.OrderResponse;
import com.example.flashsale.application.FlashSaleService;
import com.example.flashsale.domain.OrderResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flash-sale")
@Validated
@Tag(name = "Flash Sale", description = "High-concurrency flash-sale order placement")
@SecurityRequirement(name = "ApiKeyAuth")
public class FlashSaleController {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleController.class);

    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @PostMapping("/buy")
    @Operation(
        summary = "Place a flash-sale order",
        description = "Atomically reserves one unit of a product for the given user. " +
                      "Inventory is decremented in Redis via a Lua script; the order is " +
                      "persisted asynchronously through Kafka."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Order accepted and queued",
                     content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid X-API-Key"),
        @ApiResponse(responseCode = "409", description = "Duplicate concurrent request for same user"),
        @ApiResponse(responseCode = "410", description = "Product sold out"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal error — order not placed"),
    })
    public ResponseEntity<OrderResponse> buyProduct(
            @RequestParam @NotBlank @Size(max = 64) String userId,
            @RequestParam @NotBlank @Size(max = 64) String productId,
            HttpServletRequest request) {

        String correlationId = (String) request.getAttribute("correlationId");
        log.info("Order request: userId={} productId={} correlationId={}", userId, productId, correlationId);

        OrderResult result = flashSaleService.placeOrder(userId, productId);

        return switch (result) {
            case ACCEPTED -> ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(OrderResponse.of("ACCEPTED", "Order queued for processing.", correlationId));

            case SOLD_OUT -> ResponseEntity
                    .status(HttpStatus.GONE)
                    .body(OrderResponse.of("SOLD_OUT", "This product is sold out.", correlationId));

            case RATE_LIMITED -> ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(OrderResponse.of("RATE_LIMITED", "Too many requests. Please slow down.", correlationId));

            case CONCURRENT_BLOCK -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(OrderResponse.of("CONFLICT", "A duplicate request is already in-flight.", correlationId));

            case CIRCUIT_OPEN -> ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(OrderResponse.of("SERVICE_UNAVAILABLE", "Upstream dependency unavailable. Retry shortly.", correlationId));

            case ERROR -> ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OrderResponse.of("ERROR", "An internal error occurred. Please retry.", correlationId));
        };
    }
}
