// ProductoModel.java
package com.example.myapplication; // Mantenemos tu paquete original

// Ya no es necesaria la importación de Gson si no se usa para serialización/deserialización
// import com.google.gson.annotations.SerializedName;

public class ProductoModel {
    // Ya no es necesaria la anotación si no se usa Gson
    // @SerializedName("cantidad")
    private int cantidad;
    // @SerializedName("descripcion")
    private String descripcion;

    // Constructor añadido para facilitar la creación desde el parser
    public ProductoModel(int cantidad, String descripcion) {
        this.cantidad = cantidad;
        this.descripcion = descripcion;
    }

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

    @Override
    public String toString() {
        return cantidad + " x " + descripcion;
    }
}