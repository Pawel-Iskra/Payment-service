package priv.home.paymentservice.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.service.PaymentService;


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
}
