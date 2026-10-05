package priv.home.paymentservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import priv.home.paymentservice.dto.PaymentDto;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.service.PaymentService;

import java.util.Optional;


@AllArgsConstructor
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;


    @PostMapping("/create")
    public ResponseEntity<Object> createPayment(@RequestBody PaymentDto paymentDto) {
        Optional<PaymentResponse> paymentResponseOptional = paymentService.createPayment(paymentDto);

        if (paymentResponseOptional.isEmpty()) {
            return new ResponseEntity<>("Payment not added to storage", HttpStatus.OK);
        }
        return new ResponseEntity<>(paymentResponseOptional.get().toString(), HttpStatus.CREATED);
    }
}
