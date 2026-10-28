package priv.home.paymentservice.payment.api;

import iso.std.iso._20022.tech.xsd.pain_002_001.Document;
import iso.std.iso._20022.tech.xsd.pain_002_001.ObjectFactory;
import jakarta.validation.Valid;
import jakarta.xml.bind.JAXBElement;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.application.CreatePain001Result;
import priv.home.paymentservice.payment.application.CreatePaymentPain001Service;
import priv.home.paymentservice.payment.application.CreatePaymentResult;
import priv.home.paymentservice.payment.application.PaymentService;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/*
 * POST /payments/pain001 accepts limited pain.001.001.12 XML profile defined by limited-pain001.xsd.
 * The endpoint supports exactly one payment instruction containing one credit transfer transaction.
 */

@AllArgsConstructor
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";

    private final PaymentService paymentService;
    private final CreatePaymentPain001Service createPaymentPain001Service;


    @PostMapping(
            value = "/pain001",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<JAXBElement<Document>> createPaymentFromPain001(
            @RequestHeader(value = IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestBody byte[] xmlPain001Bytes)
    {
        CreatePain001Result createPain001Result = createPaymentPain001Service
                .createPaymentFromPain001Xml(idempotencyKey, xmlPain001Bytes);
        JAXBElement<Document> responsePain002XmlDocument = new ObjectFactory()
                .createDocument(createPain001Result.createPaymentStatusReportDocument());

        if (createPain001Result.wasAlreadyInDb()) {
            return ResponseEntity.ok()
                    .location(getLocation(createPain001Result.paymentId()))
                    .contentType(MediaType.APPLICATION_XML)
                    .body(responsePain002XmlDocument);
        }
        return ResponseEntity.created(getLocation(createPain001Result.paymentId()))
                .contentType(MediaType.APPLICATION_XML)
                .body(responsePain002XmlDocument);
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(value = IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest createPaymentRequest) {
        CreatePaymentResult createPaymentResult = paymentService.createPayment(idempotencyKey, createPaymentRequest);
        if (createPaymentResult.isWasAlreadyInDb()) {
            return new ResponseEntity<>(buildPaymentResponseFromCreatePaymentResult(createPaymentResult), HttpStatus.OK);
        }
        return new ResponseEntity<>(buildPaymentResponseFromCreatePaymentResult(createPaymentResult), HttpStatus.CREATED);
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


    private URI getLocation(UUID paymentId) {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/payments/{id}")
                .buildAndExpand(paymentId)
                .toUri();
    }

    //TODO: ResponseMapper, method for each case
    private PaymentResponse buildPaymentResponseFromCreatePaymentResult(CreatePaymentResult createPaymentResult) {
        return PaymentResponse.builder()
                .paymentId(createPaymentResult.getPaymentId())
                .amount(createPaymentResult.getAmount())
                .currency(createPaymentResult.getCurrency())
                .paymentStatus(createPaymentResult.getPaymentStatus())
                .build();
    }
}
