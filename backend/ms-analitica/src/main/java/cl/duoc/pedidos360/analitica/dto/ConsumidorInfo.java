package cl.duoc.pedidos360.analitica.dto;

import java.util.List;
import java.util.Map;

/**
 * Estado de los consumidores de un grupo en esta instancia.
 *
 * @param asignaciones client id de cada consumidor → particiones que tiene asignadas
 */
public record ConsumidorInfo(String id, String grupo, List<String> topicos, boolean activo, int consumidores,
        int maximo, Map<String, List<String>> asignaciones) {
}
