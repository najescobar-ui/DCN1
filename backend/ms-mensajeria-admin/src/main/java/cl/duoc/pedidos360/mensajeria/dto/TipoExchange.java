package cl.duoc.pedidos360.mensajeria.dto;

import java.util.Locale;

public enum TipoExchange {
    DIRECT, TOPIC, FANOUT, HEADERS;

    /** Nombre que usa RabbitMQ (direct, topic, ...). */
    public String amqp() {
        return name().toLowerCase(Locale.ROOT);
    }
}
