package priv.home.paymentservice.observability.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final Pattern UUID_REGEX = Pattern
            .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");


    private CorrelationIdFilter correlationIdFilter;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        correlationIdFilter = new CorrelationIdFilter();
        MDC.clear();
    }


    @Test
    public void shouldGenerateCorrelationIdWhenHeaderIsMissing() throws ServletException, IOException {
        // given
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // then
        verify(response).setHeader(eq(CORRELATION_ID_HEADER), argumentCaptor.capture());

        assertThat(UUID.fromString(argumentCaptor.getValue())).isNotNull();
        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get(MDC_CORRELATION_ID)).isNull();
    }

    @Test
    public void shouldClearMdcWhenExceptionOccur() throws ServletException, IOException {
        // given
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CORRELATION_ID_HEADER))
                .thenReturn(correlationId);
        doThrow(new ServletException("Simulated exception"))
                .when(filterChain).doFilter(request, response);

        // when + then
        assertThatThrownBy(() ->
                correlationIdFilter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(MDC_CORRELATION_ID)).isNull();
    }

    @Test
    public void shouldUseProvidedCorrelationId() throws ServletException, IOException {
        // given
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CORRELATION_ID_HEADER))
                .thenReturn(correlationId);
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        verify(response).setHeader(eq(CORRELATION_ID_HEADER), argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isEqualTo(correlationId);
    }

    @Test
    public void shouldClearMdcAfterRequest() throws ServletException, IOException {
        // given
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CORRELATION_ID_HEADER))
                .thenReturn(correlationId);

        // when
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        // then
        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get(MDC_CORRELATION_ID)).isNull();
    }

    @ParameterizedTest
    @MethodSource("invalidCorrelationIds")
    public void shouldGenerateNewCorrelationIdWhenIdFromHeaderNotMatchUuidFormat(String invalidCorrelationId) throws ServletException, IOException {
        // given
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        when(request.getHeader(CORRELATION_ID_HEADER))
                .thenReturn(invalidCorrelationId);

        // when
        correlationIdFilter.doFilterInternal(request, response, filterChain);
        verify(response).setHeader(eq(CORRELATION_ID_HEADER), argumentCaptor.capture());

        // then
        verify(filterChain).doFilter(request, response);
        assertThat(argumentCaptor.getValue())
                .isNotNull()
                .matches(UUID_REGEX);
        assertThat(MDC.get(MDC_CORRELATION_ID)).isNull();

    }


    private static Stream<String> invalidCorrelationIds() {
        return Stream.of(null, "", " ", "abcs-1212",
                UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID().toString().substring(0, 31),
                UUID.randomUUID().toString().substring(0, 20));
    }

}