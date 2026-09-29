package cl.duoc.pedidos360.productos.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cl.duoc.pedidos360.productos.model.Producto;
import cl.duoc.pedidos360.productos.repository.ProductoRepository;

/** Loads a small demo catalog the first time the database is empty. */
@Configuration
public class DataSeeder {

    @Bean
    ApplicationRunner seedProductos(ProductoRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            repository.saveAll(List.of(
                    new Producto("Pizza Napolitana", "Pizza familiar de tomate, mozzarella y albahaca", new BigDecimal("12990"), 50),
                    new Producto("Hamburguesa Doble", "Doble carne, cheddar y papas fritas", new BigDecimal("8990"), 80),
                    new Producto("Ensalada Cesar", "Lechuga, pollo grillado, crutones y parmesano", new BigDecimal("6490"), 40),
                    new Producto("Bebida 1.5L", "Bebida gaseosa 1.5 litros", new BigDecimal("2190"), 200),
                    new Producto("Brownie", "Brownie de chocolate con nueces", new BigDecimal("2990"), 60)));
        };
    }
}
