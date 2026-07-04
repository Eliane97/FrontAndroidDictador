package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;


import com.example.myapplication.service.Duplicados;
import com.example.myapplication.service.HojaRutaActivity;
import com.example.myapplication.service.Pedidos;
import com.google.android.material.card.MaterialCardView;


public class Pagprincipal extends AppCompatActivity {

    // Declaración de las variables globales para las tarjetas interactivas del menú
    private MaterialCardView cardPedidos;
    private MaterialCardView cardClientes;
    private MaterialCardView cardProductos;
    private MaterialCardView cardConfig;
    private MaterialCardView cardDuplicados;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Vincula la actividad con su diseño de interfaz gráfica en XML
        setContentView(R.layout.pagprincipal);

        // Inicializa cada uno de los componentes buscando su ID definido en el XML
        inicializarVistas();

        // Configura los escuchadores de clics para manejar la navegación de la interfaz
        configurarListeners();
    }

    /**
     * Inicializa los objetos de tipo MaterialCardView asociándolos con sus respectivos IDs del layout.
     */
    private void inicializarVistas() {
        cardPedidos = findViewById(R.id.cardPedidos);
        cardClientes = findViewById(R.id.cardClientes);
        cardProductos = findViewById(R.id.cardProductos);
        cardConfig = findViewById(R.id.cardConfig);

        // CORREGIDO: Asignamos directamente a la variable global sin redeclarar el tipo
        cardDuplicados = findViewById(R.id.cardDuplicados);
    }

    /**
     * Define la acción que se ejecutará inmediatamente después de que el usuario presione cada tarjeta.
     */
    private void configurarListeners() {

        // Configura el clic para la sección de Pedidos
        cardPedidos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Pagprincipal.this, Pedidos.class);
                startActivity(intent);
            }
        });

        /* // Configura el clic para la sección de Cuenta Corriente de Clientes
        cardClientes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Pagprincipal.this, ClientesActivity.class);
                startActivity(intent);
            }
        });
        */

        // Configura el clic para la sección de Hoja de Ruta
        cardProductos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Pagprincipal.this, HojaRutaActivity.class);
                startActivity(intent);
            }
        });

        // Configura el clic para la sección de Duplicados
        cardDuplicados.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Asegúrate de que tu actividad se llame DuplicadosActivity o Duplicados según corresponda
                Intent intent = new Intent(Pagprincipal.this, Duplicados.class);
                startActivity(intent);
            }
        });

        /*
        // Configura el clic para la sección de Control de Stock
        cardConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Pagprincipal.this, StockActivity.class);
                startActivity(intent);
            }
        });
        */
    }
}