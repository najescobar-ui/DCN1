package cl.duoc.pedidos360.analitica.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de tópicos, consumer groups y políticas de retención, centralizados en application.yml (bloque
 * "streaming"). Ninguna clase escribe estos valores a mano: todas los leen desde aquí.
 *
 * @param topicos           los dos tópicos del sistema y el de errores
 * @param grupos            un consumer group por caso de uso
 * @param concurrencia      consumidores por grupo en esta instancia (uno por partición)
 * @param ventana           tamaño de la ventana de agregación de las métricas
 * @param ventanasEnTablero cuántas ventanas guarda el tablero
 * @param reintentos        política de reintentos ante errores transitorios
 */
@ConfigurationProperties("streaming")
public record StreamingProperties(Topicos topicos, Grupos grupos, int concurrencia, Duration ventana,
        int ventanasEnTablero, Reintentos reintentos) {

    public record Topicos(Topico actividad, Topico metricas, Topico errores) {
    }

    /**
     * Definición de un tópico. Se aplica al arrancar (KafkaTopicosConfig): si el tópico no existe se crea, y si
     * existe con otra configuración, se corrige.
     *
     * @param cleanupPolicy  delete, compact o compact,delete
     * @param retencionMs    retention.ms
     * @param retencionBytes retention.bytes (por partición)
     * @param segmentoMs     segment.ms: cada cuánto se cierra un segmento (la retención borra segmentos cerrados)
     */
    public record Topico(String nombre, int particiones, short replicas, int minInsync, String cleanupPolicy,
            long retencionMs, long retencionBytes, long segmentoMs) {
    }

    /**
     * @param analitica   agrega las ventas en vivo y publica las métricas
     * @param seguimiento arma la línea de tiempo de cada pedido
     * @param tablero     lee las métricas para el tablero del administrador
     */
    public record Grupos(String analitica, String seguimiento, String tablero) {
    }

    /**
     * @param intentos intentos en total (el primero más los reintentos)
     * @param espera   pausa entre intentos
     */
    public record Reintentos(int intentos, Duration espera) {
    }
}
