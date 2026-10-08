package priv.home.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.liquibase.enabled=false")
@ActiveProfiles("local") // should be application-test.properties but stays as that
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
