// PedidoModel.java
package com.example.myapplication.model; // Mantenemos tu paquete original

import java.util.List;

public class PedidoModel {
 private String numeroPedido;
    private String fechayHoraPedido;
    private String cliente;


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


    public PedidoModel(String numeroPedido, String fechayHoraPedido, String cliente, String total, List<ProductoModel> productos) {
        this.numeroPedido = numeroPedido;
        this.fechayHoraPedido = fechayHoraPedido;
        this.cliente = cliente;
        this.total = total;
        this.productos = productos;
    }

    public String getNumeroPedido() {
        return numeroPedido;
    }

    public void setNumeroPedido(String numeroPedido) {
        this.numeroPedido = numeroPedido;
    }

    public String getFechayHoraPedido() {
        return fechayHoraPedido;
    }

    public void setFechayHoraPedido(String fechayHoraPedido) {
        this.fechayHoraPedido = fechayHoraPedido;
    }

    public void setTotal(String total) {
        this.total = total;
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
        sb.append("Pedido Nro: ").append(numeroPedido).append("\n");
        sb.append("Fecha: ").append(fechayHoraPedido).append("\n");
        sb.append("Productos:\n");
        for (ProductoModel p : productos) {
            sb.append("- ").append(p.getCantidad()).append(" ").append(p.getDescripcion())
                    .append(" | ").append(p.getPrecioTotaldelProducto()).append("\n");
        }
        sb.append("TOTAL: $").append(total);
        return sb.toString();
    }
}
