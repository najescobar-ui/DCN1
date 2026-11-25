package cl.duoc.pedidos360.streaming.dto;

/** Reglas de nombres que comparten los DTO y los parámetros de ruta. */
public final class Nombres {

    /** Caracteres que Kafka acepta en un tópico o grupo; no se permite empezar con "__" (internos). */
    public static final String PATRON_TOPICO = "^(?!__)[a-zA-Z0-9._-]{3,120}$";
    public static final String MENSAJE_TOPICO =
            "debe tener entre 3 y 120 caracteres (letras, números, punto, guion o guion bajo) y no empezar con __";
    public static final String PATRON_GRUPO = "^[a-zA-Z0-9._-]{3,120}$";
    public static final String MENSAJE_GRUPO = "debe tener entre 3 y 120 caracteres (letras, números, punto, guion o guion bajo)";

    private Nombres() {
    }
}
