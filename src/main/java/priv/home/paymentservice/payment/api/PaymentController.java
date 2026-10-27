package priv.home.paymentservice.payment.api;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import priv.home.paymentservice.payment.application.CreatePaymentResult;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.application.PaymentService;

import java.util.Optional;
import java.util.UUID;


@AllArgsConstructor
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";

    private final PaymentService paymentService;


    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(value = IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest createPaymentRequest)
    {
        CreatePaymentResult createPaymentResult = paymentService.createPayment(idempotencyKey, createPaymentRequest);
        if (createPaymentResult.isWasAlreadyInDb()) {
            return new ResponseEntity<>(buildPaymentResponseFromPaymentCreationDto(createPaymentResult), HttpStatus.OK);
        }
        return new ResponseEntity<>(buildPaymentResponseFromPaymentCreationDto(createPaymentResult), HttpStatus.CREATED);
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


    private PaymentResponse buildPaymentResponseFromPaymentCreationDto(CreatePaymentResult createPaymentResult) {
        return PaymentResponse.builder()
                .paymentId(createPaymentResult.getPaymentId())
                .amount(createPaymentResult.getAmount())
                .currency(createPaymentResult.getCurrency())
                .paymentStatus(createPaymentResult.getPaymentStatus())
                .build();
    }
}
