package cl.duoc.pedidos360.pedidos.pago;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

import cl.duoc.pedidos360.pedidos.exception.ServicioNoDisponibleException;

/** Transbank Webpay Plus REST API (v1.2): create a transaction and commit it after the customer pays. */
@Component
@EnableConfigurationProperties(PagoProperties.class)
public class WebpayClient {

    private static final String TRANSACTIONS = "/rswebpaytransaction/api/webpay/v1.2/transactions";

    private final RestClient restClient;
    private final String returnUrl;

    public WebpayClient(RestClient.Builder builder, PagoProperties properties) {
        PagoProperties.Webpay webpay = properties.webpay();
        this.returnUrl = webpay.returnUrl();
        this.restClient = builder.clone()
                .baseUrl(webpay.baseUrl())
                .defaultHeader("Tbk-Api-Key-Id", webpay.commerceCode())
                .defaultHeader("Tbk-Api-Key-Secret", webpay.apiKey())
                .build();
    }

    public record Transaccion(String token, String url) {
    }

    public record Resultado(
            String status,
            @JsonProperty("response_code") Integer responseCode,
            @JsonProperty("authorization_code") String authorizationCode,
            @JsonProperty("buy_order") String buyOrder,
            Long amount,
            @JsonProperty("card_detail") CardDetail cardDetail) {

        public boolean aprobado() {
            return "AUTHORIZED".equals(status) && Integer.valueOf(0).equals(responseCode);
        }

        public String ultimosDigitos() {
            return cardDetail == null ? null : cardDetail.cardNumber();
        }
    }

    public record CardDetail(@JsonProperty("card_number") String cardNumber) {
    }

    record CrearRequest(@JsonProperty("buy_order") String buyOrder, @JsonProperty("session_id") String sessionId,
            long amount, @JsonProperty("return_url") String returnUrl) {
    }

    public Transaccion crear(String buyOrder, String sessionId, long amount) {
        try {
            return restClient.post().uri(TRANSACTIONS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CrearRequest(buyOrder, sessionId, amount, returnUrl))
                    .retrieve()
                    .body(Transaccion.class);
        } catch (RestClientException ex) {
            throw new ServicioNoDisponibleException("No se pudo iniciar el pago en Webpay");
        }
    }

    public Resultado confirmar(String token) {
        return restClient.put().uri(TRANSACTIONS + "/{token}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Resultado.class);
    }
}
