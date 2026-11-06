package priv.home.paymentservice.payment.api.validation;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = {})
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Pattern(
        regexp = "^[a-zA-Z][a-zA-Z0-9._:-]{1,127}$",
        message = "idempotency-key must start with a letter and contain 2-128 allowed characters"
)
public @interface ValidIdempotencyKey {

    String message() default "Invalid idempotency-key";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

