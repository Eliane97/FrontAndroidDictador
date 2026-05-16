// PedidoModel.java
package com.example.myapplication.model; // Mantenemos tu paquete original

import java.util.List;

public class PedidoModel {

    private String cliente;

    // Campo para almacenar el valor que sigue a la palabra "Total" en el PDF
    private String total;
    private List<ProductoModel> productos;

    // Constructor añadido para facilitar la creación desde el parser
    public PedidoModel(String cliente, List<ProductoModel> productos) {
        this.cliente = cliente;
        this.productos = productos;
    }
    public PedidoModel(String cliente, List<ProductoModel> productos, String total) {
        this.cliente = cliente;
        this.productos = productos;
        this.total = total; // Ahora el constructor sí acepta y guarda el total
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
    public String getTotal() {
        return total;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cliente: ").append(cliente).append("\n");
        sb.append("Productos: ").append(productos.size()).append(" ítems\n");
        sb.append("Total Extraído: ").append(total);
        return sb.toString();
    }
}
