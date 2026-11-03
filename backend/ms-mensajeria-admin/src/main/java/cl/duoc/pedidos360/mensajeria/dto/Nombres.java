package cl.duoc.pedidos360.mensajeria.dto;

/** Reglas de nombres aceptadas por la API (se usan en las anotaciones de validación). */
public final class Nombres {

    /** 3 a 100 caracteres: letras, números, punto, guion y guion bajo; parte con letra o número. */
    public static final String PATRON_NOMBRE = "^[A-Za-z0-9][A-Za-z0-9._-]{2,99}$";
    public static final String MENSAJE_NOMBRE = "debe tener entre 3 y 100 caracteres (letras, números, '.', '-' o '_') y partir con letra o número";

    /** Routing key o patrón de topic: palabras separadas por punto, con comodines * y #. */
    public static final String PATRON_ROUTING_KEY = "^[A-Za-z0-9*#._-]{0,255}$";
    public static final String MENSAJE_ROUTING_KEY = "solo admite letras, números, '.', '-', '_', '*' y '#' (máximo 255)";

    private Nombres() {
    }
}
