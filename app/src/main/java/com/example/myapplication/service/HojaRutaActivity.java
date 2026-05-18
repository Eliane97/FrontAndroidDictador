package com.example.myapplication.service;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.Pagprincipal;
import com.example.myapplication.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OBJETIVO PRINCIPAL DE LA CLASE:
 * Gestionar la interfaz operativa del módulo de Hojas de Ruta de la Distribuidora.
 * Se encarga de capturar el texto crudo del informe, procesar flujos de datos iterativos continuos
 * para extraer cualquier cantidad variable de clientes, permitir la edición interactiva de la
 * grilla en memoria (CRUD) e interceptar la acción de exportación mediante un formulario de metadatos.
 * Diseña el PDF en formato vertical optimizando el espacio al máximo mediante una reducción drástica
 * del alto de las casillas de los ítems. Agrupa el título de la distribuidora, los datos logísticos
 * y el cuadro de rendición del repartidor exclusivamente en la cabecera superior de la primera hoja,
 * maximizando la superficie útil de impresión y eliminando fondos de color innecesarios.
 */
public class HojaRutaActivity extends AppCompatActivity {

    // Etiqueta de depuración para identificar registros en el Logcat
    private static final String TAG = "PDF_DEBUG";

    // Componentes de la interfaz de usuario vinculados al XML
    private EditText etDatosEntrada;
    private RecyclerView rvPedidos;
    private com.google.android.material.button.MaterialButton btnGenerar, btnCompartir, btnLimpiar, btnAgregar;
    private ImageButton btnBack;
    private ImageButton btnListaPreciosDer;

    // Estructura de datos global en memoria para sostener las filas de la tabla activa
    private final List<ItemPedido> listaPedidosGlobal = new ArrayList<>();
    private PedidosAdapter adaptadorTabla;

    /**
     * Modelo de datos que representa una fila o registro individual de pedido.
     */
    static class ItemPedido {
        String nombre;
        double importe;

        // Constructor para inicializar el objeto del cliente con su respectivo monto de deuda
        ItemPedido(String nombre, double importe) {
            this.nombre = nombre;
            this.importe = importe;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Vincula la actividad con su diseño secuencial en XML
        setContentView(R.layout.activity_hoja_ruta);

        // Mapeo de componentes visuales mediante sus identificadores únicos
        etDatosEntrada = findViewById(R.id.et_datos_entrada);
        rvPedidos = findViewById(R.id.rv_pedidos_tabla);
        btnGenerar = findViewById(R.id.btn_generar_excel);
        btnCompartir = findViewById(R.id.btn_compartir_excel);
        btnLimpiar = findViewById(R.id.btn_limpiar);
        btnAgregar = findViewById(R.id.btn_agregar_item);
        btnBack = findViewById(R.id.btn_back);
        btnListaPreciosDer = findViewById(R.id.btn_lista_precios_der);

        // Configuración estructural del RecyclerView en modo de lista lineal vertical
        rvPedidos.setLayoutManager(new LinearLayoutManager(this));
        adaptadorTabla = new PedidosAdapter();
        rvPedidos.setAdapter(adaptadorTabla);

        // EVENTO GENERAR: Extrae las coincidencias de texto y refresca la cuadrícula
        btnGenerar.setOnClickListener(v -> {
            String textoStr = etDatosEntrada.getText().toString().trim();
            if (!textoStr.isEmpty()) {
                List<ItemPedido> parsed = extraerDatosDesdeTexto(textoStr);
                if (!parsed.isEmpty()) {
                    listaPedidosGlobal.clear(); // Limpia registros obsoletos
                    listaPedidosGlobal.addAll(parsed); // Inserta el nuevo lote extraído
                    adaptadorTabla.notifyDataSetChanged(); // Forzado de redibujado de tabla
                    Toast.makeText(this, "Datos cargados en la tabla", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "No se detectaron pedidos válidos", Toast.LENGTH_LONG).show();
                }
            }
        });

        // EVENTO AGREGAR: Despliega la ventana modal configurada para inserción (-1)
        btnAgregar.setOnClickListener(v -> abrirDialogoElemento(-1));

        // EVENTO COMPARTIR: Primero solicita los datos periféricos mediante un diálogo emergente
        btnCompartir.setOnClickListener(v -> {
            if (listaPedidosGlobal.isEmpty()) {
                Toast.makeText(this, "La tabla está vacía. Añada o procese datos primero", Toast.LENGTH_SHORT).show();
                return;
            }
            solicitarDatosPerifericosYExportar();
        });

        // EVENTO LIMPIAR: Vacía los búferes de memoria y las cajas de texto de la pantalla
        btnLimpiar.setOnClickListener(v -> {
            etDatosEntrada.setText("");
            listaPedidosGlobal.clear();
            adaptadorTabla.notifyDataSetChanged();
        });
        // Configura el listener para detectar el evento de click sobre el botón de regreso
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Invoca la función personalizada encargada de realizar la navegación hacia atrás
                navegarAPantallaPrincipal();
            }
        });
        // Configura el evento de escucha para detectar cuando se presiona el botón de la agenda/lista
        btnListaPreciosDer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Ejecuta la rutina de transición para abrir el catálogo de precios
                navegarAListaPrecios();
            }
        });
    }
    /**
     * Inicia el flujo visual abriendo la pantalla dedicada a la Lista de Precios.
     */
    private void navegarAListaPrecios() {
        // Crea la intención de navegación dirigida a la clase encargada de los precios
        Intent intent = new Intent(HojaRutaActivity.this, ListaPreciosActivity.class);

        // Inicia la actividad superponiéndola en la pila de pantallas del dispositivo
        startActivity(intent);
    }
    /**
     * Función encargada de gestionar el flujo de navegación para retornar a la pantalla principal.
     * Utiliza un Intent limpio para evitar la acumulación innecesaria de actividades en la pila.
     */
    private void navegarAPantallaPrincipal() {
        // Crea un objeto Intent para definir la transición desde la actividad actual hacia MainActivity
        Intent intent = new Intent(HojaRutaActivity.this, Pagprincipal.class);

        // Añade flags para limpiar la pila de actividades, asegurando que MainActivity se reinicie o pase al frente
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Inicia la ejecución de la nueva actividad especificada en el Intent
        startActivity(intent);

        // Finaliza la actividad actual (HojaRutaActivity) para removerla por completo del flujo de la pantalla
        finish();
    }

    /**
     * Motor Regex de extracción iterativo de frontera continua.
     * Escanea secuencialmente el texto de principio a fin, aislando pares de Nombre/Monto sin importar
     * el volumen de registros suministrados en el portapapeles.
     */
    private List<ItemPedido> extraerDatosDesdeTexto(String texto) {
        List<ItemPedido> res = new ArrayList<>();

        // Expresión regular con exclusión de frontera determinista (?i)Razón:
        // Captura el nombre parando en el salto de línea, y consume secuencialmente hasta el primer Total: $
        Pattern pattern = Pattern.compile("Razón:\\s*(?:\\d+-)?\\s*([^\\r\\n]+)(?:(?!Razón:)[\\s\\S])*?Total:\\s*\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(texto);

        // El bucle "while" se ejecuta continuamente hasta agotar la última coincidencia del texto
        while (matcher.find()) {
            try {
                String nombre = matcher.group(1).trim().toUpperCase();
                String stringMonto = matcher.group(2).trim();

                // Limpieza monetaria: remueve puntos de miles y altera comas por puntos decimales estándar
                String montoNormalizado = stringMonto.replace(".", "").replace(",", ".");
                double importe = Double.parseDouble(montoNormalizado);

                // Evita cargar registros inconsistentes con importes en cero
                if (!nombre.isEmpty() && importe > 0) {
                    res.add(new ItemPedido(nombre, importe));
                }
            } catch (Exception e) {
                Log.e(TAG, "Inconsistencia aislada en procesamiento de bucle de texto");
            }
        }
        return res;
    }

    /**
     * Crea un modal dinámico en tiempo de ejecución para dar de alta o actualizar items de la grilla.
     */
    private void abrirDialogoElemento(final int posicion) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final EditText inputNombre = new EditText(this);
        inputNombre.setHint("Nombre del cliente");
        final EditText inputImporte = new EditText(this);
        inputImporte.setHint("Monto (Ej: 14500.50)");
        inputImporte.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        LinearLayout layoutModal = new LinearLayout(this);
        layoutModal.setOrientation(LinearLayout.VERTICAL);
        layoutModal.setPadding(40, 20, 40, 20);
        layoutModal.addView(inputNombre);
        layoutModal.addView(inputImporte);
        builder.setView(layoutModal);

        if (posicion != -1) {
            ItemPedido existente = listaPedidosGlobal.get(posicion);
            inputNombre.setText(existente.nombre);
            inputImporte.setText(String.valueOf(existente.importe));
            builder.setTitle("Modificar Registro");
        } else {
            builder.setTitle("Añadir Cliente a Ruta");
        }

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String name = inputNombre.getText().toString().trim().toUpperCase();
            String montoStr = inputImporte.getText().toString().trim();

            if (!name.isEmpty() && !montoStr.isEmpty()) {
                try {
                    double val = Double.parseDouble(montoStr);
                    if (posicion != -1) {
                        ItemPedido p = listaPedidosGlobal.get(posicion);
                        p.nombre = name;
                        p.importe = val;
                        adaptadorTabla.notifyItemChanged(posicion);
                    } else {
                        listaPedidosGlobal.add(new ItemPedido(name, val));
                        adaptadorTabla.notifyItemInserted(listaPedidosGlobal.size() - 1);
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Formato numérico incorrecto", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    /**
     * Despliega una ventana modal interactiva para capturar los datos periféricos de logística
     * requeridos antes de iniciar la construcción del archivo PDF.
     */
    private void solicitarDatosPerifericosYExportar() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Datos Periféricos de la Ruta");

        final EditText inputZona = new EditText(this);
        inputZona.setHint("Zona de Reparto (Ej: Sierras / Centro)");

        final EditText inputFechaVenta = new EditText(this);
        inputFechaVenta.setHint("Fecha de Venta (Ej: 14/05/2026)");

        final EditText inputFechaEntrega = new EditText(this);
        inputFechaEntrega.setHint("Fecha de Entrega (Ej: 15/05/2026)");

        LinearLayout layoutPerifericos = new LinearLayout(this);
        layoutPerifericos.setOrientation(LinearLayout.VERTICAL);
        layoutPerifericos.setPadding(44, 24, 44, 24);
        layoutPerifericos.addView(inputZona);
        layoutPerifericos.addView(inputFechaVenta);
        layoutPerifericos.addView(inputFechaEntrega);
        builder.setView(layoutPerifericos);

        builder.setPositiveButton("Generar PDF", (dialog, which) -> {
            String zona = inputZona.getText().toString().trim();
            String fVenta = inputFechaVenta.getText().toString().trim();
            String fEntrega = inputFechaEntrega.getText().toString().trim();

            if (zona.isEmpty()) zona = "General";
            if (fVenta.isEmpty()) fVenta = "-";
            if (fEntrega.isEmpty()) fEntrega = "-";

            exportarYDescargarPdfReal(zona, fVenta, fEntrega);
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    /**
     * Dibuja y genera vectorialmente las celdas de la Hoja de Ruta para su volcado a PDF.
     * MODIFICADO: Pone el cuadro de rendición manual arriba a la derecha únicamente en la primera página.
     */
    private void exportarYDescargarPdfReal(String zonaParam, String fechaVentaParam, String fechaEntregaParam) {
        PdfDocument doc = new PdfDocument();
        // Definición del formato de página A4 estándar (595x842 puntos)
        PdfDocument.PageInfo pInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();

        // Inicializamos la primera página del documento de forma explícita
        PdfDocument.Page paginaActual = doc.startPage(pInfo);
        Canvas canvasActual = paginaActual.getCanvas();

        Paint p = new Paint();
        Paint tp = new Paint();
        tp.setAntiAlias(true);

        String[] columnas = {"Num", "Nombre del cliente", "Importe", "Pago", "Debe", "Entrega", "Devuelto"};
        int[] posX = {30, 65, 230, 300, 365, 430, 500};

        // Altura de fila achicada al extremo (13 puntos) para optimizar el espacio impreso
        int altoF = 13;

        // Puntero vertical dinámico de coordenadas del lienzo Y
        int yAct = 75;
        int limiteInferiorHoja = 780; // Incrementado levemente el área de resguardo al no tener el cuadro al final

        // --- ENCABEZADO SUPERIOR EXCLUSIVO DE LA HOJA PRINCIPAL ---
        // Pintamos el título de la distribuidora en el cuadrante superior izquierdo
        tp.setTextSize(13f); tp.setFakeBoldText(true); tp.setColor(Color.parseColor("#121B2A"));
        canvasActual.drawText("Distribuidora Godoy", 30, 35, tp);

        // Imprime los datos base logísticos debajo del título principal
        tp.setTextSize(8.5f); tp.setFakeBoldText(false); tp.setColor(Color.BLACK);
        canvasActual.drawText("Zona: " + zonaParam + "   |   F. Venta: " + fechaVentaParam + "   |   F. Entrega: " + fechaEntregaParam, 30, 52, tp);

        // MODIFICADO: UBICACIÓN DEL CUADRO DE RENDICIÓN MANUAL (Arriba a la derecha - Primera Página)
        p.setColor(Color.parseColor("#475569"));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.8f);
        // Dibujamos el contorno del cuadro de rendición en el margen derecho de la cabecera
        canvasActual.drawRect(310, 20, 565, yAct - 15, p);

        // Rellenamos el cuadro de rendición manual con sus textos e indicadores de línea para el chofer
        tp.setTextSize(8f); tp.setFakeBoldText(false);
        canvasActual.drawText("Plata + Fiado: ___________ ", 318, 33, tp);
        canvasActual.drawText("Venta + Cobranza: ______________", 432, 33, tp);
        canvasActual.drawText("Gasto: _______________ Importe:____________", 318, 50, tp);


        // --- DIBUJO DE CABECERA PRINCIPAL DE LA TABLA ---
        // Se dibuja directo un recuadro transparente con contorno gris sin color azul de fondo
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.8f); p.setColor(Color.parseColor("#94A3B8"));
        canvasActual.drawRect(30, yAct, 565, yAct + 18, p);

        // Setea las fuentes del encabezado en negro para economizar tinta de impresión
        tp.setTextSize(9f); tp.setFakeBoldText(true); tp.setColor(Color.BLACK);
        for (int j = 0; j < columnas.length; j++) {
            canvasActual.drawText(columnas[j], posX[j] + 4, yAct + 13, tp);
            if (posX[j] > 30) canvasActual.drawLine(posX[j], yAct, posX[j], yAct + 18, p);
        }
        yAct += 18; // Posiciona el puntero Y justo debajo del encabezado de la primera hoja

        double totalGlobalAcumulado = 0;

        // Ciclo principal de dibujo: Vuelca todos los clientes de la lista al documento de manera fluida y multipágina
        for (int i = 0; i < listaPedidosGlobal.size(); i++) {
            ItemPedido item = listaPedidosGlobal.get(i);

            // DETECCIÓN Y CAMBIO DE HOJA ESTRICTO
            if (yAct + altoF > limiteInferiorHoja) {
                doc.finishPage(paginaActual); // Cerramos FORMALMENTE la hoja previa

                paginaActual = doc.startPage(pInfo); // Instanciamos una página limpia de continuación
                canvasActual = paginaActual.getCanvas(); // Vinculamos su lienzo vectorial

                // Redibujamos el encabezado secundario limpio desde arriba (Subimos a yAct = 42 para ganar espacio)
                yAct = 42;

                // Mantiene el estilo transparente con bordes grises sin títulos institucionales ni nro de página
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.8f); p.setColor(Color.parseColor("#94A3B8"));
                canvasActual.drawRect(30, yAct, 565, yAct + 18, p);

                tp.setTextSize(9f); tp.setFakeBoldText(true); tp.setColor(Color.BLACK);
                for (int j = 0; j < columnas.length; j++) {
                    canvasActual.drawText(columnas[j], posX[j] + 4, yAct + 13, tp);
                    if (posX[j] > 30) canvasActual.drawLine(posX[j], yAct, posX[j], yAct + 18, p);
                }
                yAct += 18; // Desplaza cursor debajo de la cabecera secundaria
            }

            // Efecto cebra para las celdas intercaladas de la grilla
            if (i % 2 == 1) {
                p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#F1F5F9"));
                canvasActual.drawRect(30, yAct, 565, yAct + altoF, p);
                p.setStyle(Paint.Style.STROKE); p.setColor(Color.parseColor("#94A3B8"));
            }

            // Dibujo del borde rectangular externo de la fila actual
            canvasActual.drawRect(30, yAct, 565, yAct + altoF, p);

            // Fuente ultra-compacta (7f) para que entre perfectamente en la casilla miniatura
            tp.setTextSize(7f); tp.setFakeBoldText(false); tp.setColor(Color.BLACK);

            // Centrado vertical calibrado a +10 para amoldarse a los 13 puntos de alto
            canvasActual.drawText(String.valueOf(i + 1), posX[0] + 4, yAct + 10, tp);
            canvasActual.drawText(item.nombre, posX[1] + 4, yAct + 10, tp);
            canvasActual.drawText(String.format("$ %,.2f", item.importe), posX[2] + 4, yAct + 10, tp);

            // Trazado de líneas verticales internas de separación de celdas
            for (int x : posX) {
                if (x > 30) canvasActual.drawLine(x, yAct, x, yAct + altoF, p);
            }
            totalGlobalAcumulado += item.importe;
            yAct += altoF; // Incrementamos el cursor vertical para la próxima iteración del bucle
        }

        // VALIDACIÓN DE ESPACIO FINAL EXCLUSIVAMENTE PARA LA FILA DE TOTALES (Bloque mínimo requerido de 35 puntos)
        if (yAct + altoF + 35 > limiteInferiorHoja) {
            doc.finishPage(paginaActual);
            paginaActual = doc.startPage(pInfo);
            canvasActual = paginaActual.getCanvas();

            yAct = 42; // Posición superior inicial en la hoja final de cierre

            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.8f); p.setColor(Color.parseColor("#94A3B8"));
            canvasActual.drawRect(30, yAct, 565, yAct + 18, p);

            tp.setTextSize(9f); tp.setFakeBoldText(true); tp.setColor(Color.BLACK);
            for (int j = 0; j < columnas.length; j++) {
                canvasActual.drawText(columnas[j], posX[j] + 4, yAct + 13, tp);
                if (posX[j] > 30) canvasActual.drawLine(posX[j], yAct, posX[j], yAct + 18, p);
            }
            yAct += 18;
        }

        // Fila de clausura para mostrar el subtotal/total general de la grilla
        p.setStyle(Paint.Style.STROKE); p.setColor(Color.parseColor("#94A3B8"));
        canvasActual.drawRect(30, yAct, 565, yAct + 18, p);
        tp.setTextSize(9f); tp.setFakeBoldText(true); tp.setColor(Color.BLACK);
        canvasActual.drawText("TOTAL:", posX[1] + 4, yAct + 13, tp);
        canvasActual.drawText(String.format("$ %,.2f", totalGlobalAcumulado), posX[2] + 4, yAct + 13, tp);
        canvasActual.drawLine(posX[2], yAct, posX[2], yAct + 18, p);

        // Se cierra la última página activa del documento de manera formal
        doc.finishPage(paginaActual);

        // Canalización IO para volcar el documento PDF construido en el almacenamiento de descargas
        try {
            File dest = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "HojaRuta_Distribuidora.pdf");
            FileOutputStream out = new FileOutputStream(dest);
            doc.writeTo(out);
            out.close();
            doc.close(); // Liberamos los descriptores de memoria del documento

            Uri safeUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", dest);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, safeUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Descargar y enviar PDF via:"));
        } catch (IOException e) {
            Log.e(TAG, "Excepción de guardado en disco", e);
        }
    }

    /**
     * Adaptador interno optimizado para manejar operaciones CRUD básicas sobre los items.
     */
    class PedidosAdapter extends RecyclerView.Adapter<PedidosAdapter.TablaViewHolder> {

        @NonNull
        @Override
        public TablaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pedido_tabla, parent, false);
            return new TablaViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TablaViewHolder holder, int position) {
            ItemPedido current = listaPedidosGlobal.get(position);
            holder.tvNum.setText(String.valueOf(position + 1));
            holder.tvNombre.setText(current.nombre);
            holder.tvImporte.setText(String.format("$ %,.2f", current.importe));

            holder.btnEditar.setOnClickListener(v -> abrirDialogoElemento(holder.getAdapterPosition()));

            holder.btnEliminar.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listaPedidosGlobal.remove(pos);
                    notifyItemRemoved(pos);
                    notifyItemRangeChanged(pos, listaPedidosGlobal.size());
                }
            });
        }

        @Override
        public int getItemCount() {
            return listaPedidosGlobal.size();
        }

        class TablaViewHolder extends RecyclerView.ViewHolder {
            TextView tvNum, tvNombre, tvImporte;
            ImageButton btnEditar, btnEliminar;

            TablaViewHolder(View itemView) {
                super(itemView);
                tvNum = itemView.findViewById(R.id.tv_item_num);
                tvNombre = itemView.findViewById(R.id.tv_item_nombre);
                tvImporte = itemView.findViewById(R.id.tv_item_importe);
                btnEditar = itemView.findViewById(R.id.btn_item_editar);
                btnEliminar = itemView.findViewById(R.id.btn_item_eliminar);
            }
        }
    }
}