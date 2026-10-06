package priv.home.paymentservice.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.exceptions.SinglePaymentNotFoundException;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.service.PaymentService;

import java.util.Optional;
import java.util.UUID;


@AllArgsConstructor
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;


    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        PaymentResponse paymentResponse = paymentService.createPayment(paymentRequest);
        return new ResponseEntity<>(paymentResponse, HttpStatus.CREATED);
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
