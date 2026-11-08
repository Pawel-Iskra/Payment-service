package priv.home.paymentservice.payment.api;

import iso.std.iso._20022.tech.xsd.pain_002_001.Document;
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
import priv.home.paymentservice.payment.api.mapper.PaymentJsonResponseMapper;
import priv.home.paymentservice.payment.api.mapper.PaymentXmlResponseMapper;
import priv.home.paymentservice.payment.api.validation.ValidIdempotencyKey;
import priv.home.paymentservice.payment.application.*;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;

import java.net.URI;
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
    private final PaymentJsonResponseMapper paymentJsonResponseMapper;
    private final PaymentXmlResponseMapper paymentXmlResponseMapper;

    // TODO: in between task:
    //  - common errors enum for validation Json/Xml


    @PostMapping(
            value = "/pain001",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<JAXBElement<Document>> createPaymentFromPain001(
            @RequestHeader(name = IDEMPOTENCY_KEY) @ValidIdempotencyKey String idempotencyKey,
            @RequestBody byte[] xmlPain001Bytes)
    {
        CreatePain001Result createPain001Result = createPaymentPain001Service
                .createPaymentFromPain001Xml(idempotencyKey, xmlPain001Bytes);
        URI location = getLocation(createPain001Result.paymentId());

        if (createPain001Result.wasAlreadyInDb()) {
            return ResponseEntity.ok()
                    .location(location)
                    .body(paymentXmlResponseMapper.buildXmlResponse(createPain001Result));
        }
        return ResponseEntity.created(location)
                .body(paymentXmlResponseMapper.buildXmlResponse(createPain001Result));
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(name = IDEMPOTENCY_KEY) @ValidIdempotencyKey String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest createPaymentRequest)
    {
        CreatePaymentResult createPaymentResult = paymentService.createPayment(idempotencyKey, createPaymentRequest);
        URI location = getLocation(createPaymentResult.paymentId());

        if (createPaymentResult.wasAlreadyInDb()) {
            return ResponseEntity.ok()
                    .location(location)
                    .body(paymentJsonResponseMapper.buildPaymentJsonResponse(createPaymentResult));
        }
        return ResponseEntity.created(location)
                .body(paymentJsonResponseMapper.buildPaymentJsonResponse(createPaymentResult));
    }

    @GetMapping(
            value = "/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> retrievePayment(@PathVariable("id") UUID paymentId)
    {
        PaymentRetrieveResult paymentRetrieveResult = paymentService
                .retrieveSinglePaymentByPaymentId(paymentId)
                .orElseThrow(() -> new SinglePaymentNotFoundException(paymentId));
        return new ResponseEntity<>(paymentJsonResponseMapper
                .buildPaymentJsonResponse(paymentRetrieveResult), HttpStatus.OK);
    }


    private URI getLocation(UUID paymentId) {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/payments/{id}")
                .buildAndExpand(paymentId)
                .toUri();
    }
}
