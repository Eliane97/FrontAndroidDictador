// ProductoModel.java
package com.example.myapplication.model; // Mantenemos tu paquete original

// Ya no es necesaria la importación de Gson si no se usa para serialización/deserialización
// import com.google.gson.annotations.SerializedName;

public class ProductoModel {
    // Ya no es necesaria la anotación si no se usa Gson
    // @SerializedName("cantidad")
    private int cantidad;
    // @SerializedName("descripcion")
    private String descripcion;
    // NUEVO: Atributo para almacenar el costo o precio unitario del producto
    private String precio;

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    private String categoria;

    public int getCodigo() {
        return codigo;
    }

    public void setCodigo(int codigo) {
        this.codigo = codigo;
    }

    private int codigo;
    // Constructor añadido para facilitar la creación desde el parser
    public ProductoModel(int cantidad, String descripcion) {
        this.cantidad = cantidad;
        this.descripcion = descripcion;
    }
    public ProductoModel(int cantidad, String descripcion, String precio, int codigo) {
        this.cantidad = cantidad;
        this.descripcion = descripcion;
        this.precio = precio; // Inicializa el nuevo atributo con el valor recibido
        this.codigo= codigo;
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

    // NUEVO: Método Getter para recuperar el precio unitario
    public String getPrecio() {
        return precio;
    }

    // NUEVO: Método Setter para modificar el precio unitario si es necesario
    public void setPrecio(String precio) {
        this.precio = precio;
    }

    /**
     * Sobrescribe el método toString estándar para formatear la salida del objeto de una forma
     * completamente legible, ideal para depuración o registros rápidos en consola.
     */
    @Override
    public String toString() {
        // Retorna una cadena estructurada con el formato: "Cantidad x Descripción ($Precio)"
        return cantidad + " x " + descripcion + " ($" + precio + ")";
    }
}