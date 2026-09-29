package cl.duoc.pedidos360.pedidos.pago;

import java.net.URI;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import cl.duoc.pedidos360.pedidos.exception.OperacionNoPermitidaException;
import cl.duoc.pedidos360.pedidos.model.EstadoPago;
import cl.duoc.pedidos360.pedidos.model.EstadoPedido;
import cl.duoc.pedidos360.pedidos.model.MetodoPago;
import cl.duoc.pedidos360.pedidos.model.Pedido;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import cl.duoc.pedidos360.pedidos.service.Usuario;

@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final PedidoService pedidoService;
    private final PedidoRepository repository;
    private final WebpayClient webpay;
    private final PagoProperties properties;

    public PagoService(PedidoService pedidoService, PedidoRepository repository, WebpayClient webpay,
            PagoProperties properties) {
        this.pedidoService = pedidoService;
        this.repository = repository;
        this.webpay = webpay;
        this.properties = properties;
    }

    /** Creates a Webpay transaction for the order; the frontend then posts token_ws to the returned URL. */
    @Transactional
    public WebpayClient.Transaccion iniciarWebpay(Long pedidoId, Usuario usuario, String origen) {
        Pedido pedido = pedidoService.buscarAutorizado(pedidoId, usuario);
        if (pedido.getMetodoPago() != MetodoPago.TARJETA) {
            throw new OperacionNoPermitidaException("El pedido no se paga con tarjeta");
        }
        if (pedido.getEstadoPago() == EstadoPago.PAGADO || pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new OperacionNoPermitidaException("El pedido ya esta pagado o fue cancelado");
        }
        // buy_order: max 26 chars, unique per attempt.
        String buyOrder = "P360-" + pedido.getId() + "-" + Long.toString(System.currentTimeMillis(), 36);
        WebpayClient.Transaccion transaccion = webpay.crear(buyOrder, String.valueOf(pedido.getId()),
                pedido.getTotal().longValueExact());
        pedido.iniciarPagoWebpay(transaccion.token(), properties.origenPermitido(origen));
        return transaccion;
    }

    /**
     * Handles the customer coming back from Webpay and returns where to send the browser.
     * token_ws alone = finished payment (commit it); TBK_TOKEN = cancelled; neither = timeout.
     */
    @Transactional
    public URI procesarRetorno(String tokenWs, String tbkToken, String ordenCompra) {
        Optional<Pedido> encontrado = Optional.ofNullable(tokenWs != null ? tokenWs : tbkToken)
                .flatMap(repository::findByPagoToken)
                .or(() -> pedidoDesdeOrden(ordenCompra));
        if (encontrado.isEmpty()) {
            return destino(properties.frontendOrigins().get(0), "error", null);
        }
        Pedido pedido = encontrado.get();
        String resultado;
        if (tokenWs != null && tbkToken == null) {
            resultado = confirmar(pedido, tokenWs);
        } else {
            pedido.cambiarEstadoPago(EstadoPago.RECHAZADO);
            resultado = "anulado";
        }
        return destino(properties.origenPermitido(pedido.getPagoOrigen()), resultado, pedido.getId());
    }

    private String confirmar(Pedido pedido, String token) {
        try {
            WebpayClient.Resultado resultado = webpay.confirmar(token);
            if (resultado.aprobado() && resultado.amount() != null
                    && resultado.amount() == pedido.getTotal().longValueExact()) {
                pedido.registrarPagoAprobado(resultado.authorizationCode(), resultado.ultimosDigitos());
                return "aprobado";
            }
            log.info("Webpay rechazo el pago del pedido {}: status={} code={}", pedido.getId(), resultado.status(),
                    resultado.responseCode());
        } catch (RestClientException ex) {
            log.warn("No se pudo confirmar el pago del pedido {} en Webpay: {}", pedido.getId(), ex.getMessage());
        }
        pedido.cambiarEstadoPago(EstadoPago.RECHAZADO);
        return "rechazado";
    }

    private Optional<Pedido> pedidoDesdeOrden(String ordenCompra) {
        if (ordenCompra == null || !ordenCompra.startsWith("P360-")) {
            return Optional.empty();
        }
        try {
            return repository.findById(Long.valueOf(ordenCompra.split("-")[1]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
            return Optional.empty();
        }
    }

    private static URI destino(String origen, String resultado, Long pedidoId) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(origen).path("/pedidos").queryParam("pago", resultado);
        if (pedidoId != null) {
            uri.queryParam("pedido", pedidoId);
        }
        return uri.build().toUri();
    }
}
