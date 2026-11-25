package cl.duoc.pedidos360.streaming.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.streaming.dto.Respuestas.ClusterInfo;
import cl.duoc.pedidos360.streaming.service.KafkaAdminService;

/** GET /api/streaming/cluster: brokers, controlador, cantidad de tópicos y de grupos. */
@RestController
@RequestMapping("/api/streaming/cluster")
public class ClusterController {

    private final KafkaAdminService service;

    public ClusterController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ClusterInfo cluster() {
        return service.cluster();
    }
}
