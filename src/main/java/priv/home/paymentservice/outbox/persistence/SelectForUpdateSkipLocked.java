package priv.home.paymentservice.outbox.persistence;


import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(
        value = @QueryHint(
                name = "jakarta.persistence.lock.timeout",
                value = "-2"    // lock.timeout = -2 -> SKIP_LOCKED
        ), forCounting = false
)
public @interface SelectForUpdateSkipLocked {
}