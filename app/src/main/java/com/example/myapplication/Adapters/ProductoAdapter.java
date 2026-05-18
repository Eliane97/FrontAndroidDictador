package com.example.myapplication.Adapters;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.model.ProductoModel;

import java.util.List;
import java.util.Locale;

/**
 * OBJETIVO PRINCIPAL:
 * Servir como intermediario directo entre la colección de datos 'listaProductos' y la
 * interfaz visual del RecyclerView, inflando el layout de la fila e inyectando las
 * propiedades correspondientes a cada celda en la posición asignada.
 */
public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder> {

    // Lista contenedora de los datos comerciales que se van a dibujar
    private final List<ProductoModel> productosList;

    // Constructor que recibe la referencia de la lista de productos desde la actividad
    public ProductoAdapter(List<ProductoModel> productosList) {
        this.productosList = productosList;
    }

    @NonNull
    @Override
    public ProductoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Infla la estructura visual jerárquica diseñada para un renglón individual de la tabla
        View vistaItem = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_producto_tabla, parent, false);
        return new ProductoViewHolder(vistaItem);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoViewHolder holder, int position) {
        ProductoModel producto = productosList.get(position);

        // Asignación directa de los componentes de texto básicos a la vista de la fila
        holder.tvCodigo.setText(String.valueOf(producto.getCodigo()));
        holder.tvDescripcion.setText(producto.getDescripcion());

        // FIDELIDAD ABSOLUTA: Al ser un String, no pasa por procesadores matemáticos.
        // Si el CSV dice "2,093.00", mostrará "$2,093.00". Si dice "266", mostrará "$266".
        String precioFinalAVisualizar = "$" + producto.getPrecio();

        // Inyecta el texto estricto tal cual vino del Excel en el TextView de la interfaz
        holder.tvPrecio.setText(precioFinalAVisualizar);
    }

    @Override
    public int getItemCount() {
        // Indica de forma exacta al sistema cuántas celdas o elementos debe instanciar el RecyclerView
        return productosList != null ? productosList.size() : 0;
    }

    /**
     * Clase estática encargada de almacenar temporalmente los componentes gráficos de un renglón,
     * optimizando el rendimiento de la lista al evitar invocaciones duplicadas a findViewById.
     */
    public static class ProductoViewHolder extends RecyclerView.ViewHolder {
        TextView tvCodigo;
        TextView tvDescripcion;
        TextView tvPrecio;

        public ProductoViewHolder(@NonNull View itemView) {
            super(itemView);
            // Sincroniza las referencias de los objetos Java con los elementos del archivo XML de fila
            tvCodigo = itemView.findViewById(R.id.tv_item_codigo);
            tvDescripcion = itemView.findViewById(R.id.tv_item_descripcion);
            tvPrecio = itemView.findViewById(R.id.tv_item_precio);
        }
    }
}