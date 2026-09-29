package cl.duoc.pedidos360.pedidos.pago;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param frontendOrigins frontends allowed to start a payment; the first one is the default return target
 * @param webpay          Transbank Webpay Plus REST settings
 */
@ConfigurationProperties("app.pagos")
public record PagoProperties(List<String> frontendOrigins, Webpay webpay) {

    /**
     * @param baseUrl      Transbank environment (integration: https://webpay3gint.transbank.cl)
     * @param commerceCode Tbk-Api-Key-Id
     * @param apiKey       Tbk-Api-Key-Secret
     * @param returnUrl    public URL Webpay sends the customer back to (API Gateway route to this service)
     */
    public record Webpay(String baseUrl, String commerceCode, String apiKey, String returnUrl) {
    }

    public String origenPermitido(String origen) {
        return origen != null && frontendOrigins.contains(origen) ? origen : frontendOrigins.get(0);
    }
}
