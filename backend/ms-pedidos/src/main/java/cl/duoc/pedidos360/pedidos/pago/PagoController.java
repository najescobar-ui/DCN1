package cl.duoc.pedidos360.pedidos.pago;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.pedidos.service.Usuario;

@RestController
public class PagoController {

    private final PagoService service;

    public PagoController(PagoService service) {
        this.service = service;
    }

    public record IniciarPagoRequest(String origen) {
    }

    /** Starts a card payment; the frontend must POST token_ws to the returned url. */
    @PostMapping("/api/pedidos/{id}/pago/webpay")
    public WebpayClient.Transaccion iniciar(@PathVariable Long id, @RequestBody(required = false) IniciarPagoRequest request,
            JwtAuthenticationToken auth) {
        return service.iniciarWebpay(id, Usuario.from(auth), request == null ? null : request.origen());
    }

    /**
     * Public endpoint Webpay redirects the browser to (GET or form POST). The transaction is
     * validated server to server with Transbank, then the browser goes back to the frontend.
     */
    @RequestMapping(path = "/api/pagos/webpay/retorno", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<Void> retorno(@RequestParam(name = "token_ws", required = false) String tokenWs,
            @RequestParam(name = "TBK_TOKEN", required = false) String tbkToken,
            @RequestParam(name = "TBK_ORDEN_COMPRA", required = false) String ordenCompra) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(service.procesarRetorno(tokenWs, tbkToken, ordenCompra))
                .build();
    }
}
