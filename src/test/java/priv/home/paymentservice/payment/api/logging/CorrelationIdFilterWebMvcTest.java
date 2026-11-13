package priv.home.paymentservice.payment.api.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import priv.home.paymentservice.payment.api.errorhandling.ControllerExceptionHandler;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = CorrelationIdFilterWebMvcTest.TestController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = ControllerExceptionHandler.class
        ))
@Import({CorrelationIdFilter.class, CorrelationIdFilterWebMvcTest.TestController.class})
class CorrelationIdFilterWebMvcTest {


    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";


    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestController {
        @GetMapping("/test")
        String test() {
            return MDC.get(MDC_CORRELATION_ID);
        }

        @GetMapping("/test/exception")
        String testException() throws ServletException {
            throw new ServletException("Simulated exception");
        }
    }

    @BeforeEach
    void setUp() {
        MDC.clear();
    }


    @Test
    public void shouldUseProvidedCorrelationIdAndClearMdcAfterRequest() throws Exception {
        // given
        String correlationId = UUID.randomUUID().toString();

        // when + then
        mockMvc.perform(get("/test")
                        .header(CORRELATION_ID_HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CORRELATION_ID_HEADER, correlationId))
                .andExpect(content().string(correlationId));

        assertThat(MDC.get(MDC_CORRELATION_ID)).isNull();
    }

    @Test
    public void shouldClearMdcWhenExceptionOccurs() {
        // given
        String correlationId = UUID.randomUUID().toString();

        // when + then
        assertThatThrownBy(() -> mockMvc.perform(get("/test/exception")
                .header(CORRELATION_ID_HEADER, correlationId)))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(MDC_CORRELATION_ID)).isNull();
    }

}