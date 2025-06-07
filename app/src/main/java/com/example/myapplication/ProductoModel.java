package com.example.myapplication;

import com.google.gson.annotations.SerializedName;

public class ProductoModel {
    @SerializedName("cantidad")
    private int cantidad;
    @SerializedName("descripcion")
    private String descripcion;

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}