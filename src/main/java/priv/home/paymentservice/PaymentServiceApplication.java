package priv.home.paymentservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

	@Bean
    CommandLineRunner testKafka(KafkaTemplate<String, String> kafkaTemplate) {
		return args -> {
			kafkaTemplate.send("payment-created", "payment-created _ 1")
					.get(10, TimeUnit.SECONDS);

			System.out.println("Połączenie z Kafką działa!");
		};
	}

}
