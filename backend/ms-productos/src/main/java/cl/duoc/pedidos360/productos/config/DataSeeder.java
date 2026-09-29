package cl.duoc.pedidos360.productos.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cl.duoc.pedidos360.productos.model.Producto;
import cl.duoc.pedidos360.productos.repository.ProductoRepository;

/** Loads the demo catalog the first time the database is empty. Images are served by the frontend. */
@Configuration
public class DataSeeder {

    @Bean
    ApplicationRunner seedProductos(ProductoRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            repository.saveAll(List.of(
                    producto("Hamburguesa doble cheddar", "Dos carnes smash de 120 g, doble cheddar fundido, pepinillos y salsa de la casa en pan brioche.",
                            "Hamburguesas", "8990", "10990", 40, "hamburguesa-queso.jpg"),
                    producto("Smash burger clasica", "Carne smash de 150 g con bordes crujientes, cheddar, pepinillos y cebolla morada.",
                            "Hamburguesas", "6990", null, 50, "hamburguesa-smash.jpg"),
                    producto("Pizza pepperoni familiar", "Masa artesanal de 40 cm, salsa de tomate, mozzarella y abundante pepperoni.",
                            "Pizzas", "12990", "15990", 25, "pizza-familiar.jpg"),
                    producto("Pizza napolitana individual", "Tomate, mozzarella fresca, albahaca y aceite de oliva sobre masa delgada.",
                            "Pizzas", "7490", null, 30, "pizza-napolitana.jpg"),
                    producto("Promo 30 piezas", "Surtido de rolls: california, tempura y sake maki. Incluye soya y jengibre.",
                            "Sushi", "14990", "17990", 20, "sushi-platter.jpg"),
                    producto("Gyozas de cerdo (6)", "Empanaditas japonesas selladas a la plancha con salsa ponzu.",
                            "Sushi", "4990", null, 45, "gyozas.jpg"),
                    producto("Bebida lata 350 ml", "Bebida gaseosa en lata, bien helada.",
                            "Bebidas", "1290", null, 200, "lata.jpg"),
                    producto("Brownie con helado", "Brownie tibio de chocolate con nueces y una bola de helado de vainilla.",
                            "Postres", "3990", null, 35, "brownie.jpg")));
        };
    }

    private static Producto producto(String nombre, String descripcion, String categoria, String precio,
            String precioAnterior, int stock, String imagen) {
        return new Producto(nombre, descripcion, categoria, new BigDecimal(precio),
                precioAnterior == null ? null : new BigDecimal(precioAnterior), stock, "/img/productos/" + imagen);
    }
}
