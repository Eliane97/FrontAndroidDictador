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
 * Se encarga de capturar el texto crudo del informe, procesar flujos de datos iterativos continuos
 * para extraer de forma exacta cualquier cantidad variable de clientes, permitir la edición
 * interactiva de la grilla en memoria (CRUD) y exportar el resultado final a un documento
 * PDF dinámico multi-página, garantizando que no se corten los registros sin importar el volumen total.
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

        // Mapeo de componentes visuales mediante sus identificadores únicos
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
     * CORREGIDO: Soporta múltiples páginas de forma automática calculando los límites físicos del papel A4.
     */
    private void exportarYDescargarPdfReal() {
        PdfDocument doc = new PdfDocument();
        // Configura tamaño A4 estándar: 595 de ancho por 842 de alto (puntos PostScript)
        PdfDocument.PageInfo pInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();

        // Arreglos de un solo elemento para poder modificar los objetos de página y canvas dentro del bloque Runnable
        final PdfDocument.Page[] paginaActual = { doc.startPage(pInfo) };
        final Canvas[] canvasActual = { paginaActual[0].getCanvas() };

        Paint p = new Paint();
        Paint tp = new Paint();
        tp.setAntiAlias(true); // Suavizado de bordes tipográficos

        // Definición matemática de coordenadas de columnas basadas en la planilla base
        String[] columnas = {"Num", "Nombre del cliente", "Importe", "Pago", "Debe", "Entrega", "Entregado/vuelto"};
        int[] posX = {30, 65, 230, 300, 365, 430, 500};
        int altoF = 24; // Altura fija de cada renglón

        // Punteros mutables envueltos en matrices para actualización de referencias en sub-procesos
        final int[] yAct = { 90 };
        final int[] nroPagina = { 1 };

        // Margen de seguridad antes de tocar el final de la hoja física (842)
        int limiteInferiorHoja = 780;

        // Sub-rutina lambda encargada de dibujar los rótulos fijos y membretes cada vez que nace una página
        Runnable dibujarEncabezadoHoja = () -> {
            // Renderizado del Membrete Comercial de la Distribuidora
            tp.setTextSize(14f); tp.setFakeBoldText(true); tp.setColor(Color.parseColor("#121B2A"));
            canvasActual[0].drawText("Distribuidora Godoy", 30, 45, tp);

            tp.setTextSize(10f); tp.setFakeBoldText(false);
            canvasActual[0].drawText("Reparto - Zona: Sierras", 30, 65, tp);
            canvasActual[0].drawText("Pág. " + nroPagina[0], 520, 45, tp); // Indicador numérico de página activa
            canvasActual[0].drawText("Fecha Entrega: Viern 15/05/26", 380, 65, tp);

            // Dibujo del bloque contenedor de la cabecera (Azul Institucional)
            p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#1E3A8A"));
            canvasActual[0].drawRect(30, 90, 565, 90 + altoF, p);

            // Rotulado de textos superiores dentro del rectángulo azul
            tp.setTextSize(9f); tp.setFakeBoldText(true); tp.setColor(Color.WHITE);
            for (int j = 0; j < columnas.length; j++) {
                canvasActual[0].drawText(columnas[j], posX[j] + 4, 90 + 15, tp);
            }

            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(0.8f); p.setColor(Color.parseColor("#94A3B8"));
            yAct[0] = 90 + altoF; // Restablece el cursor vertical justo debajo de los encabezados fijos
        };

        // Ejecuta la impresión inicial del membrete para la hoja número uno
        dibujarEncabezadoHoja.run();
        double totalGlobalAcumulado = 0;

        // Ciclo principal de dibujo: Transfiere la lista total de memoria al lienzo vectorial
        for (int i = 0; i < listaPedidosGlobal.size(); i++) {
            ItemPedido item = listaPedidosGlobal.get(i);

            // DETECCIÓN DE DESBORDE: Si la próxima celda excede los 780 puntos, salta de hoja de inmediato
            if (yAct[0] + altoF > limiteInferiorHoja) {
                doc.finishPage(paginaActual[0]); // Clausura y guarda el estado de la hoja llena
                nroPagina[0]++; // Incrementa el contador general

                paginaActual[0] = doc.startPage(pInfo); // Genera un nuevo lienzo en blanco
                canvasActual[0] = paginaActual[0].getCanvas(); // Reasigna el canvas de dibujo operativo

                dibujarEncabezadoHoja.run(); // Vuelve a estampar la cabecera azul en la hoja nueva
            }

            // Efecto cebra para filas impares (Fondo gris tenue para agilizar la lectura del chofer)
            if (i % 2 == 1) {
                p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#F1F5F9"));
                canvasActual[0].drawRect(30, yAct[0], 565, yAct[0] + altoF, p);
                p.setStyle(Paint.Style.STROKE); p.setColor(Color.parseColor("#94A3B8"));
            }

            // Traza el marco exterior perimetral de la celda activa
            canvasActual[0].drawRect(30, yAct[0], 565, yAct[0] + altoF, p);
            tp.setFakeBoldText(false); tp.setColor(Color.BLACK);

            // Inyección de textos planos formateados dentro de sus respectivas columnas vectoriales
            canvasActual[0].drawText(String.valueOf(i + 1), posX[0] + 4, yAct[0] + 15, tp);
            canvasActual[0].drawText(item.nombre, posX[1] + 4, yAct[0] + 15, tp);
            canvasActual[0].drawText(String.format("$ %,.2f", item.importe), posX[2] + 4, yAct[0] + 15, tp);

            // Segmentación y trazado de líneas verticales divisorias
            for (int x : posX) {
                if (x > 30) canvasActual[0].drawLine(x, yAct[0], x, yAct[0] + altoF, p);
            }
            totalGlobalAcumulado += item.importe;
            yAct[0] += altoF; // Desplaza el cursor vertical proporcionalmente al alto de fila
        }

        // VALIDACIÓN DE CIERRE PARA EL TOTAL: Si la fila final no entra en la última hoja, salta una más
        if (yAct[0] + altoF > limiteInferiorHoja) {
            doc.finishPage(paginaActual[0]);
            nroPagina[0]++;
            paginaActual[0] = doc.startPage(pInfo);
            canvasActual[0] = paginaActual[0].getCanvas();
            dibujarEncabezadoHoja.run();
        }

        // Fila de clausura definitiva para el cálculo y muestra del Total General
        p.setStyle(Paint.Style.STROKE); p.setColor(Color.parseColor("#94A3B8"));
        canvasActual[0].drawRect(30, yAct[0], 565, yAct[0] + altoF, p);
        tp.setFakeBoldText(true); tp.setColor(Color.BLACK);
        canvasActual[0].drawText("TOTALES:", posX[1] + 4, yAct[0] + 15, tp);
        canvasActual[0].drawText(String.format("$ %,.2f", totalGlobalAcumulado), posX[2] + 4, yAct[0] + 15, tp);
        canvasActual[0].drawLine(posX[2], yAct[0], posX[2], yAct[0] + altoF, p);

        // Finaliza y cierra de forma segura la última hoja del lote procesado
        doc.finishPage(paginaActual[0]);

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