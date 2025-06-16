// PedidoModel.java
package com.example.myapplication; // Mantenemos tu paquete original

// Ya no es necesaria la importación de Gson si no se usa para serialización/deserialización
// import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PedidoModel {
    // Ya no es necesaria la anotación si no se usa Gson
    // @SerializedName("cliente")
    private String cliente;
    // @SerializedName("productos")
    private List<ProductoModel> productos;

    // Constructor añadido para facilitar la creación desde el parser
    public PedidoModel(String cliente, List<ProductoModel> productos) {
        this.cliente = cliente;
        this.productos = productos;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public List<ProductoModel> getProductos() {
        return productos;
    }

    public void setProductos(List<ProductoModel> productos) {
        this.productos = productos;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cliente: ").append(cliente).append("\n");
        sb.append("Productos:\n");
        for (ProductoModel p : productos) {
            sb.append("  - ").append(p.toString()).append("\n");
        }
        return sb.toString();
    }
}
