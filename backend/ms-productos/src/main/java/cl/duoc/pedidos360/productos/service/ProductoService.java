package cl.duoc.pedidos360.productos.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.pedidos360.productos.dto.ProductoRequest;
import cl.duoc.pedidos360.productos.dto.ProductoResponse;
import cl.duoc.pedidos360.productos.exception.RecursoNoEncontradoException;
import cl.duoc.pedidos360.productos.model.Producto;
import cl.duoc.pedidos360.productos.repository.ProductoRepository;

@Service
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    public List<ProductoResponse> listar() {
        return repository.findByActivoTrueOrderByNombreAsc().stream().map(ProductoResponse::from).toList();
    }

    public ProductoResponse obtener(Long id) {
        return ProductoResponse.from(buscar(id));
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {
        Producto producto = new Producto(request.nombre(), request.descripcion(), request.precio(), request.stock());
        return ProductoResponse.from(repository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscar(id);
        producto.actualizar(request.nombre(), request.descripcion(), request.precio(), request.stock());
        return ProductoResponse.from(producto);
    }

    /** Soft delete: existing orders keep referencing the product. */
    @Transactional
    public void eliminar(Long id) {
        buscar(id).desactivar();
    }

    private Producto buscar(Long id) {
        return repository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto " + id + " no existe"));
    }
}
