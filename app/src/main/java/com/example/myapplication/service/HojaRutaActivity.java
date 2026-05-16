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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
 * Se encarga de capturar el texto crudo del informe, parsearlo mediante expresiones regulares,
 * permitir la edición interactiva de los datos (CRUD en memoria) y exportar el resultado final
 * a un documento PDF estructurado vectorialmente para su distribución.
 */
public class HojaRutaActivity extends AppCompatActivity {

    // Etiqueta de depuración para identificar registros en el Logcat
    private static final String TAG = "PDF_DEBUG";

    // Componentes de la interfaz de usuario vinculados al XML
    private EditText etDatosEntrada;
    private RecyclerView rvPedidos;
    private com.google.android.material.button.MaterialButton btnGenerar, btnCompartir, btnLimpiar, btnAgregar;

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

        // Mapeo exhaustivo de componentes visuales mediante sus identificadores únicos
        etDatosEntrada = findViewById(R.id.et_datos_entrada);
        rvPedidos = findViewById(R.id.rv_pedidos_tabla);
        btnGenerar = findViewById(R.id.btn_generar_excel);
        btnCompartir = findViewById(R.id.btn_compartir_excel);
        btnLimpiar = findViewById(R.id.btn_limpiar);
        btnAgregar = findViewById(R.id.btn_agregar_item);

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

        // EVENTO COMPARTIR: Valida datos existentes y compila la matriz gráfica a PDF
        btnCompartir.setOnClickListener(v -> {
            if (listaPedidosGlobal.isEmpty()) {
                Toast.makeText(this, "La tabla está vacía. Añada o procese datos primero", Toast.LENGTH_SHORT).show();
                return;
            }
            exportarYDescargarPdfReal();
        });

        // EVENTO LIMPIAR: Vacía los búferes de memoria y las cajas de texto de la pantalla
        btnLimpiar.setOnClickListener(v -> {
            etDatosEntrada.setText("");
            listaPedidosGlobal.clear();
            adaptadorTabla.notifyDataSetChanged();
        });
    }

    /**
     * Motor Regex optimizado para escanear y deserializar los bloques del informe de distribución.
     */
    private List<ItemPedido> extraerDatosDesdeTexto(String texto) {
        List<ItemPedido> res = new ArrayList<>();
        // Patrón compilado para buscar la Razón Social y capturar de forma perezosa hasta el Total numérico
        Pattern pattern = Pattern.compile("Razón:\\s*(?:\\d+-)?(.*.*?)\\r?\\n(?:.*?\\r?\\n)*?Total:\\s*\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(texto);

        // Bucle iterativo de búsqueda sobre las coincidencias del texto ingresado
        while (matcher.find()) {
            try {
                String nombre = matcher.group(1).trim().toUpperCase();
                String stringMonto = matcher.group(2).trim();
                // Limpieza monetaria: remueve puntos de miles y altera comas por puntos decimales estándar
                String montoNormalizado = stringMonto.replace(".", "").replace(",", ".");
                double importe = Double.parseDouble(montoNormalizado);
                res.add(new ItemPedido(nombre, importe));
            } catch (Exception e) {
                Log.e(TAG, "Inconsistencia de parseo en línea de texto");
            }
        }
        return res;
    }

    /**
     * Crea un modal dinámico en tiempo de ejecución para dar de alta o actualizar items de la grilla.
     */
    private void abrirDialogoElemento(final int posicion) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Elementos de edición construidos de forma nativa para evitar dependencias infladas
        final EditText inputNombre = new EditText(this);
        inputNombre.setHint("Nombre del cliente");
        final EditText inputImporte = new EditText(this);
        inputImporte.setHint("Monto (Ej: 14500.50)");
        inputImporte.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        // Contenedor lineal para encapsular ambos campos verticalmente dentro de la alerta
        android.widget.LinearLayout layoutModal = new android.widget.LinearLayout(this);
        layoutModal.setOrientation(android.widget.LinearLayout.VERTICAL);
        layoutModal.setPadding(40, 20, 40, 20);
        layoutModal.addView(inputNombre);
        layoutModal.addView(inputImporte);
        builder.setView(layoutModal);

        // Control de flujo: Determina si es una edición de fila o un registro en blanco
        if (posicion != -1) {
            ItemPedido existente = listaPedidosGlobal.get(posicion);
            inputNombre.setText(existente.nombre);
            inputImporte.setText(String.valueOf(existente.importe));
            builder.setTitle("Modificar Registro");
        } else {
            builder.setTitle("Añadir Cliente a Ruta");
        }

        // Lógica de confirmación y guardado seguro del modal
        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String name = inputNombre.getText().toString().trim().toUpperCase();
            String montoStr = inputImporte.getText().toString().trim();

            if (!name.isEmpty() && !montoStr.isEmpty()) {
                try {
                    double val = Double.parseDouble(montoStr);
                    if (posicion != -1) { // Sobrescribe la entidad existente
                        ItemPedido p = listaPedidosGlobal.get(posicion);
                        p.nombre = name;
                        p.importe = val;
                        adaptadorTabla.notifyItemChanged(posicion);
                    } else { // Inserta una nueva fila al final del array
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
     * Dibuja y genera vectorialmente las celdas de la Hoja de Ruta para su volcado a PDF.
     */
    private void exportarYDescargarPdfReal() {
        PdfDocument doc = new PdfDocument();
        // Configura tamaño A4 estándar: 595 de ancho por 842 de alto (puntos PostScript)
        PdfDocument.PageInfo pInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page pagina = doc.startPage(pInfo);
        Canvas canvas = pagina.getCanvas();

        Paint p = new Paint();
        Paint tp = new Paint();
        tp.setAntiAlias(true); // Suavizado de bordes tipográficos

        // Renderizado del Membrete Comercial e Información General de la Distribuidora
        tp.setTextSize(14f); tp.setFakeBoldText(true); tp.setColor(Color.parseColor("#121B2A"));
        canvas.drawText("Distribuidora Godoy", 30, 45, tp);

        tp.setTextSize(10f); tp.setFakeBoldText(false);
        canvas.drawText("Reparto - Zona: Sierras", 30, 65, tp);
        canvas.drawText("Fecha Entrega: Viern 15/05/26", 380, 65, tp);

        // Definición matemática de coordenadas de columnas basadas en la planilla base
        String[] columnas = {"Num", "Nombre del cliente", "Importe", "Pago", "Debe", "Entrega", "Entregado/vuelto"};
        int[] posX = {30, 65, 230, 300, 365, 430, 500};
        int yIn = 90; int altoF = 24;

        // Dibujo del bloque contenedor de la cabecera (Azul Institucional)
        p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#1E3A8A"));
        canvas.drawRect(30, yIn, 565, yIn + altoF, p);

        // Rotulado de textos superiores dentro del rectángulo azul
        tp.setTextSize(9f); tp.setFakeBoldText(true); tp.setColor(Color.WHITE);
        for (int i = 0; i < columnas.length; i++) {
            canvas.drawText(columnas[i], posX[i] + 4, yIn + 15, tp);
        }

        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.8f); p.setColor(Color.parseColor("#94A3B8"));
        int yAct = yIn + altoF;
        double total = 0;

        // Ciclo principal de dibujo: Transfiere la lista en memoria al lienzo vectorial
        for (int i = 0; i < listaPedidosGlobal.size(); i++) {
            ItemPedido item = listaPedidosGlobal.get(i);

            // Efecto cebra para filas impares (Fondo gris tenue)
            if (i % 2 == 1) {
                p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#F1F5F9"));
                canvas.drawRect(30, yAct, 565, yAct + altoF, p);
                p.setStyle(Paint.Style.STROKE); p.setColor(Color.parseColor("#94A3B8"));
            }

            // Traza el marco exterior de la celda actual
            canvas.drawRect(30, yAct, 565, yAct + altoF, p);
            tp.setFakeBoldText(false); tp.setColor(Color.BLACK);

            // Inyección de cadenas de caracteres puras dentro de las coordenadas de la fila
            canvas.drawText(String.valueOf(i + 1), posX[0] + 4, yAct + 15, tp);
            canvas.drawText(item.nombre, posX[1] + 4, yAct + 15, tp);
            canvas.drawText(String.format("$ %,.2f", item.importe), posX[2] + 4, yAct + 15, tp);

            // Segmentación interna de líneas verticales divisorias
            for (int x : posX) {
                if (x > 30) canvas.drawLine(x, yAct, x, yAct + altoF, p);
            }
            total += item.importe;
            yAct += altoF; // Salto de línea dinámico proporcional a la altura fijada
        }

        // Fila de clausura para el cálculo y muestra del Total General
        canvas.drawRect(30, yAct, 565, yAct + altoF, p);
        tp.setFakeBoldText(true);
        canvas.drawText("TOTALES:", posX[1] + 4, yAct + 15, tp);
        canvas.drawText(String.format("$ %,.2f", total), posX[2] + 4, yAct + 15, tp);
        canvas.drawLine(posX[2], yAct, posX[2], yAct + altoF, p);

        doc.finishPage(pagina);

        // Proceso de guardado en la carpeta compartida pública de descargas
        try {
            File dest = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "HojaRuta_Sierras.pdf");
            FileOutputStream out = new FileOutputStream(dest);
            doc.writeTo(out);
            out.close();
            doc.close();

            // Envoltura segura del URI mediante FileProvider para evitar excepciones de exposición
            Uri safeUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", dest);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, safeUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Descargar y enviar PDF via:"));
        } catch (IOException e) {
            Log.e(TAG, "Excepción crítica de guardado en disco", e);
        }
    }

    /**
     * Adaptador interno optimizado para manejar operaciones CRUD básicas sobre los items.
     */
    class PedidosAdapter extends RecyclerView.Adapter<PedidosAdapter.TablaViewHolder> {

        @NonNull
        @Override
        public TablaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Infla el layout diseñado de celdas unitarias para la grilla
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pedido_tabla, parent, false);
            return new TablaViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TablaViewHolder holder, int position) {
            ItemPedido current = listaPedidosGlobal.get(position);
            // Formatea el componente numérico sumándole base 1 para indexación humana
            holder.tvNum.setText(String.valueOf(position + 1));
            holder.tvNombre.setText(current.nombre);
            holder.tvImporte.setText(String.format("$ %,.2f", current.importe));

            // Escuchador táctil asignado para alterar registros mediante modales emergentes
            holder.btnEditar.setOnClickListener(v -> abrirDialogoElemento(holder.getAdapterPosition()));

            // Escuchador táctil asignado para el borrado físico de la fila en tiempo de ejecución
            holder.btnEliminar.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listaPedidosGlobal.remove(pos);
                    notifyItemRemoved(pos); // Animación nativa de remoción
                    notifyItemRangeChanged(pos, listaPedidosGlobal.size()); // Desplazamiento de índices remanentes
                }
            });
        }

        @Override
        public int getItemCount() {
            return listaPedidosGlobal.size(); // Retorna el volumen actual de elementos de la lista
        }

        /**
         * Contenedor de vistas interno para el mapeo de celdas.
         */
        class TablaViewHolder extends RecyclerView.ViewHolder {
            TextView tvNum, tvNombre, tvImporte;
            ImageButton btnEditar, btnEliminar;

            // CORREGIDO: El nombre del constructor ahora coincide exactamente con el nombre de la clase interna
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