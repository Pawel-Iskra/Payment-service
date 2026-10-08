package priv.home.paymentservice.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.exceptions.SinglePaymentNotFoundException;
import priv.home.paymentservice.model.PaymentCreationResponse;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.service.PaymentService;

import java.util.Optional;
import java.util.UUID;


@AllArgsConstructor
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";

    private final PaymentService paymentService;


    @PostMapping
    public ResponseEntity<PaymentCreationResponse> createPayment(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = true) String idempotencyKey,
            @Valid @RequestBody PaymentRequest paymentRequest
    ) {

        PaymentCreationResponse paymentCreationResponse = paymentService.createPayment(idempotencyKey, paymentRequest);
        if (paymentCreationResponse.isWasAlreadyInDb()) {
            return new ResponseEntity<>(paymentCreationResponse, HttpStatus.OK);
        }
        return new ResponseEntity<>(paymentCreationResponse, HttpStatus.CREATED);
    }


    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> retrievePayment(@PathVariable("id") UUID paymentId) {
        Optional<PaymentResponse> paymentResponseOptional =
                paymentService.retrieveSinglePaymentByPaymentId(paymentId);
        if (paymentResponseOptional.isPresent()) {
            return new ResponseEntity<>(paymentResponseOptional.get(), HttpStatus.OK);
        } else {
            throw new SinglePaymentNotFoundException(paymentId);
        }
    }
}
