package com.example.myapplication;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PedidoModel {
    @SerializedName("cliente")
    private String cliente;
    @SerializedName("productos")
    private List<ProductoModel> productos;

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
}