package cl.duoc.pedidos360.streaming.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.streaming.dto.PublicarEventosRequest;
import cl.duoc.pedidos360.streaming.dto.Respuestas.Publicado;
import cl.duoc.pedidos360.streaming.service.EventosDemoService;
import jakarta.validation.Valid;

/** POST /api/streaming/events: publica eventos de prueba (202 con la partición y el offset de cada uno). */
@RestController
@RequestMapping("/api/streaming/events")
public class EventoDemoController {

    private final EventosDemoService service;

    public EventoDemoController(EventosDemoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<Publicado> publicar(@Valid @RequestBody PublicarEventosRequest request) {
        return service.publicar(request);
    }
}
