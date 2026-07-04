package com.example.myapplication.service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.myapplication.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Duplicados extends AppCompatActivity {

    private static final String TAG = "PDF_DIRECTO_DEBUG";

    private EditText etDatosEntrada;
    private com.google.android.material.button.MaterialButton btnCompartir, btnLimpiar;
    private ImageButton btnBack;

    // Modelo de datos interno para estructurar la información extraída
    static class PedidoCompleto {
        String idPedido = "";
        String emision = "";
        String razonSocial = "";
        String facturacion = "";
        String totalStr = "";
        List<String> productos = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_duplicados);

        etDatosEntrada = findViewById(R.id.et_datos_entrada);
        btnCompartir = findViewById(R.id.btn_compartir_excel);
        btnLimpiar = findViewById(R.id.btn_limpiar);
        btnBack = findViewById(R.id.btn_back);

        // AL PRESIONAR: Procesa el texto plano, genera el PDF simétrico de fondo y abre el menú de descarga/envío
        btnCompartir.setOnClickListener(v -> {
            String textoStr = etDatosEntrada.getText().toString().trim();
            if (textoStr.isEmpty()) {
                Toast.makeText(this, "Por favor, pegue el texto de los pedidos primero", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. Extraer estructuradamente la información de cada pedido
            List<PedidoCompleto> pedidosDetectados = extraerPedidosDesdeTexto(textoStr);
            if (pedidosDetectados.isEmpty()) {
                Toast.makeText(this, "No se detectaron bloques de pedido válidos en el texto", Toast.LENGTH_LONG).show();
                return;
            }

            // 2. Generar el PDF y guardarlo en el almacenamiento público de Descargas
            File archivoPdfFinal = generarYGuardarPdf(pedidosDetectados);

            // 3. Si todo salió bien, disparar el menú nativo de Android
            if (archivoPdfFinal != null && archivoPdfFinal.exists()) {
                abrirMenuCompartir(archivoPdfFinal);
            } else {
                Toast.makeText(this, "Ocurrió un problema al construir el archivo PDF", Toast.LENGTH_SHORT).show();
            }
        });

        btnLimpiar.setOnClickListener(v -> etDatosEntrada.setText(""));
        btnBack.setOnClickListener(v -> finish());
    }

    /**
     * Motor Regex optimizado: Escanea secuencialmente aislando cada bloque de pedido
     * y extrayendo de forma precisa sus cabeceras junto a la lista completa de artículos.
     */
    private List<PedidoCompleto> extraerPedidosDesdeTexto(String texto) {
        List<PedidoCompleto> resultados = new ArrayList<>();
        String[] bloques = texto.split("<<<<<\\s*PEDIDO:");

        for (String bloque : bloques) {
            if (!bloque.contains("Total:")) continue;
            try {
                PedidoCompleto pedido = new PedidoCompleto();

                Pattern pId = Pattern.compile("^(\\d+)");
                Matcher mId = pId.matcher(bloque.trim());
                if (mId.find()) pedido.idPedido = mId.group(1);

                Pattern pEmision = Pattern.compile("Emisión:\\s*([^\\r\\n]+)");
                Matcher mEmision = pEmision.matcher(bloque);
                if (mEmision.find()) pedido.emision = mEmision.group(1).trim();

                Pattern pRazon = Pattern.compile("Razón:\\s*([^\\r\\n]+)");
                Matcher mRazon = pRazon.matcher(bloque);
                if (mRazon.find()) pedido.razonSocial = mRazon.group(1).trim().toUpperCase();

                Pattern pFact = Pattern.compile("Facturación:\\s*([^\\r\\n]+)");
                Matcher mFact = pFact.matcher(bloque);
                if (mFact.find()) pedido.facturacion = mFact.group(1).trim();

                Pattern pTotal = Pattern.compile("Total:\\s*\\$\\s*([\\d.,]+)");
                Matcher mTotal = pTotal.matcher(bloque);
                if (mTotal.find()) pedido.totalStr = mTotal.group(1).trim();

                // Extracción de productos delimitados por las líneas discontinuas
                if (bloque.contains("------------------------------")) {
                    String[] partesTabla = bloque.split("------------------------------");
                    if (partesTabla.length > 1) {
                        String cuerpoProductos = partesTabla[1];
                        String[] lineasProductos = cuerpoProductos.split("\n");
                        for (String lProd : lineasProductos) {
                            lProd = lProd.trim();
                            if (lProd.isEmpty()) continue;
                            if (lProd.contains("itens") || lProd.contains("vols.")) break;
                            pedido.productos.add(lProd);
                        }
                    }
                }
                if (!pedido.idPedido.isEmpty()) resultados.add(pedido);
            } catch (Exception e) {
                Log.e(TAG, "Error aislando bloque de pedido", e);
            }
        }
        return resultados;
    }

    /**
     * Dibuja y empaqueta el documento duplicado (2 pedidos por página A4)
     * directo hacia la carpeta pública de descargas del dispositivo.
     */
    private File generarYGuardarPdf(List<PedidoCompleto> listaPedidos) {
        PdfDocument doc = new PdfDocument();
        int anchoHoja = 595;
        int altoHoja = 842;
        int mitadHoja = altoHoja / 2;

        Paint pBordes = new Paint();
        pBordes.setStyle(Paint.Style.STROKE); pBordes.setStrokeWidth(0.8f); pBordes.setColor(Color.parseColor("#94A3B8"));
        Paint pTexto = new Paint(); pTexto.setAntiAlias(true);

        int totalPedidos = listaPedidos.size();
        int indexPedido = 0;
        int nroPagina = 1;

        while (indexPedido < totalPedidos) {
            PdfDocument.PageInfo pInfo = new PdfDocument.PageInfo.Builder(anchoHoja, altoHoja, nroPagina).create();
            PdfDocument.Page pagina = doc.startPage(pInfo);
            Canvas canvas = pagina.getCanvas();

            // 1. Dibujar Pedido Superior en la hoja activa
            dibujarPedidoIndividual(canvas, listaPedidos.get(indexPedido), 0, mitadHoja, pTexto, pBordes, anchoHoja);
            indexPedido++;

            // Línea central de guía para corte/troquel manual
            pBordes.setColor(Color.parseColor("#CBD5E1"));
            canvas.drawLine(15, mitadHoja, anchoHoja - 15, mitadHoja, pBordes);
            pBordes.setColor(Color.parseColor("#94A3B8"));

            // 2. Dibujar Pedido Inferior en la misma hoja (si queda alguno en la lista)
            if (indexPedido < totalPedidos) {
                dibujarPedidoIndividual(canvas, listaPedidos.get(indexPedido), mitadHoja, mitadHoja, pTexto, pBordes, anchoHoja);
                indexPedido++;
            }

            doc.finishPage(pagina);
            nroPagina++;
        }

        try {
            // Guardado directo en la carpeta de Descargas del Teléfono
            File dest = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pedidos_Duplicados_Distribuidora.pdf");
            FileOutputStream out = new FileOutputStream(dest);
            doc.writeTo(out);
            out.close();
            doc.close();
            return dest;
        } catch (IOException e) {
            Log.e(TAG, "Excepción escribiendo PDF en disco", e);
            return null;
        }
    }

    /**
     * Pinta de manera compacta y simétrica los componentes de un pedido individual.
     */
    private void dibujarPedidoIndividual(Canvas canvas, PedidoCompleto pedido, int yOffset, int altoMaximo, Paint pText, Paint pBox, int anchoHoja) {
        int margenX = 30;
        int anchoUtil = anchoHoja - (margenX * 2);
        int yCursor = yOffset + 30;

        // --- ENCABEZADO ---
        // Nombre Distribuidora (Azul Corporativo)
        pText.setAntiAlias(true);
        pText.setTextSize(15f);
        pText.setFakeBoldText(true);
        pText.setColor(Color.parseColor("#1E3A8A")); // Azul Oscuro
        pText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("DISTRIBUIDORA GODOY", margenX, yCursor, pText);

        // Tipo de Comprobante (Derecha)
        pText.setTextSize(11f);
        pText.setColor(Color.BLACK);
        String tipoComp = "REMITO / COMPROBANTE";
        float anchoTipo = pText.measureText(tipoComp);
        canvas.drawText(tipoComp, anchoHoja - margenX - anchoTipo, yCursor, pText);

        // Subtítulo Izquierda
        yCursor += 14;
        pText.setTextSize(8.5f);
        pText.setFakeBoldText(false);
        pText.setColor(Color.parseColor("#4B5563")); // Gris
        pText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText("ENTREGA DE MERCADERÍA", margenX, yCursor, pText);

        // Metadata Pedido (Derecha)
        String metaData = "PEDIDO: " + pedido.idPedido + "  |  Fecha: " + pedido.emision;
        float anchoMeta = pText.measureText(metaData);
        canvas.drawText(metaData, anchoHoja - margenX - anchoMeta, yCursor, pText);

        // --- BLOQUE DATOS DEL CLIENTE ---
        yCursor += 10;
        // RECUADRO SIN RELLENO PARA AHORRO DE TINTA
        pBox.setStyle(Paint.Style.STROKE);
        pBox.setStrokeWidth(0.8f);
        pBox.setColor(Color.parseColor("#9CA3AF")); // Borde Gris Claro
        canvas.drawRoundRect(margenX, yCursor, anchoHoja - margenX, yCursor + 22, 6, 6, pBox);

        pText.setTextSize(9f);
        pText.setColor(Color.BLACK);
        pText.setFakeBoldText(true);
        canvas.drawText(" Cliente: ", margenX + 6, yCursor + 14, pText);

        pText.setFakeBoldText(false);
        float anchoLabelCliente = pText.measureText(" Cliente: ");
        canvas.drawText(pedido.razonSocial, margenX + 6 + anchoLabelCliente, yCursor + 14, pText);

        // --- TABLA DE PRODUCTOS (DOBLE COLUMNA CON VLR. UNIT) ---
        yCursor += 32;

        // Encabezado Tabla Principal (SOLO BORDE AZUL - SIN RELLENO)
        pBox.setStyle(Paint.Style.STROKE);
        pBox.setStrokeWidth(1.2f);
        pBox.setColor(Color.parseColor("#1E40AF")); // Azul Marino
        canvas.drawRoundRect(margenX, yCursor, anchoHoja - margenX, yCursor + 16, 4, 4, pBox);

        pText.setTextSize(7.2f);
        pText.setColor(Color.parseColor("#1E40AF")); // Texto en azul oscuro para combinar con el borde
        pText.setFakeBoldText(true);
        pText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        // Títulos de columnas (cantidades, descripción y valor unitario abreviados)
        // Distribución fija: CANT (3) | DESCRIPCIÓN (18) | V.U (7) -> Total 30 caracteres por columna
        String headerTxt = "CAN DESCRIPCION       V.UNIT ";
        canvas.drawText(headerTxt, margenX + 6, yCursor + 11, pText);
        canvas.drawText(headerTxt, margenX + (anchoUtil / 2) + 12, yCursor + 11, pText);

        yCursor += 16;
        pText.setColor(Color.parseColor("#1F2937")); // Texto oscuro para ítems
        pText.setFakeBoldText(false);
        pText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        // Distribución de productos en 2 columnas
        int totalItems = pedido.productos.size();
        int itemsPorColumna = (int) Math.ceil(totalItems / 2.0);
        int altoLinea = 12; // Un toque de margen vertical extra ya que no hay líneas divisoras

        for (int i = 0; i < itemsPorColumna; i++) {
            int yFila = yCursor + (i * altoLinea);

            // --- Columna 1 (Izquierda) ---
            String prodCol1 = pedido.productos.get(i);
            String formateado1 = formatearLineaProductoConPrecio(prodCol1);
            canvas.drawText(formateado1, margenX + 6, yFila + 9, pText);

            // --- Columna 2 (Derecha - Si existe) ---
            int indexCol2 = i + itemsPorColumna;
            if (indexCol2 < totalItems) {
                String prodCol2 = pedido.productos.get(indexCol2);
                String formateado2 = formatearLineaProductoConPrecio(prodCol2);
                canvas.drawText(formateado2, margenX + (anchoUtil / 2) + 12, yFila + 9, pText);
            }
        }

        // Calcular el salto dinámico que usó la tabla
        yCursor += (itemsPorColumna * altoLinea) + 12;

        // --- PIE DE PÁGINA (TOTALES Y FIRMAS) ---
        // Contenedor de "Total a Pagar" (SOLO BORDE VERDE - SIN RELLENO)
        int anchoTotalBox = 160;
        int xTotalBox = anchoHoja - margenX - anchoTotalBox;
        pBox.setStyle(Paint.Style.STROKE);
        pBox.setStrokeWidth(1.2f);
        pBox.setColor(Color.parseColor("#15803D")); // Borde Verde Corporativo
        canvas.drawRoundRect(xTotalBox, yCursor, anchoHoja - margenX, yCursor + 22, 6, 6, pBox);

        pText.setTextSize(9f);
        pText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        pText.setColor(Color.parseColor("#166534")); // Texto Verde Oscuro
        canvas.drawText("TOTAL A PAGAR:", xTotalBox + 10, yCursor + 14, pText);

        String txtMonto = "$" + pedido.totalStr;
        float anchoMonto = pText.measureText(txtMonto);
        canvas.drawText(txtMonto, anchoHoja - margenX - 10 - anchoMonto, yCursor + 14, pText);

        // Cantidad de Items Totales (Izquierda - Solo contorno fino)
        pBox.setStyle(Paint.Style.STROKE);
        pBox.setStrokeWidth(0.6f);
        pBox.setColor(Color.parseColor("#D1D5DB"));
        canvas.drawRoundRect(margenX, yCursor + 2, margenX + 80, yCursor + 20, 4, 4, pBox);
        pText.setColor(Color.parseColor("#374151"));
        pText.setTextSize(8f);
        canvas.drawText(totalItems + " Ítems Totales", margenX + 8, yCursor + 13, pText);

        // --- SECCIÓN DE FIRMAS ---
        yCursor += 45;
        pBox.setStyle(Paint.Style.STROKE);
        pBox.setStrokeWidth(0.6f);
        pBox.setColor(Color.parseColor("#9CA3AF")); // Línea Gris
        pText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        pText.setTextSize(8f);
        pText.setColor(Color.parseColor("#6B7280"));

        // Firma Distribuidora
        int xFirma1 = margenX + 20;
        canvas.drawLine(xFirma1, yCursor, xFirma1 + 140, yCursor, pBox);
        canvas.drawText("Firma Responsable Distribuidora", xFirma1 + 10, yCursor + 10, pText);

        // Firma Cliente
        int xFirma2 = anchoHoja - margenX - 160;
        canvas.drawLine(xFirma2, yCursor, xFirma2 + 140, yCursor, pBox);
        canvas.drawText("Firma Conforme Cliente", xFirma2 + 25, yCursor + 10, pText);
    }

    /**
     * Función auxiliar optimizada que procesa la línea del producto original,
     * extrae la cantidad, recorta la descripción e inserta el valor unitario (Vlr. unit)
     * alineándolo de forma perfecta a un ancho exacto de 30 caracteres.
     */
    private String formatearLineaProductoConPrecio(String rawProduct) {
        if (rawProduct == null || rawProduct.isEmpty()) return "";

        // Unificamos espacios repetidos
        String limpio = rawProduct.replaceAll("\\s+", " ").trim();

        // Separamos por espacios
        String[] tokens = limpio.split(" ");
        if (tokens.length < 2) return limpio;

        // El primer token es la cantidad
        String cant = tokens[0];

        // Buscaremos el valor unitario. En el formato original, el penúltimo valor suele ser el unitario
        // (Ej: "98 2 Pan Integral avena x 470g 0,00 0,00" o "156 18 Alf Rasta negro 962,50 17.325,00")
        String vUnit = "$0.00";
        int indexPrecioUnitario = tokens.length - 2;
        if (indexPrecioUnitario > 0) {
            vUnit = tokens[indexPrecioUnitario];
            // Quitar decimales innecesarios ",00" para ahorrar aún más espacio horizontal si es un entero
            if (vUnit.endsWith(",00")) {
                vUnit = vUnit.substring(0, vUnit.length() - 3);
            }
        }

        // Reconstruir la descripción excluyendo código inicial (si existe), cantidad, vUnit y vTotal
        // Buscamos dónde termina la cantidad y dónde empiezan los precios
        StringBuilder descBuilder = new StringBuilder();
        // Empezamos desde el token 1 (saltando cantidad) hasta el token del precio unitario
        for (int i = 1; i < indexPrecioUnitario; i++) {
            // Opcional: si el primer token de descripción es el código numérico del producto (ej: "98", "165"), lo saltamos para ganar espacio
            if (i == 1 && tokens[i].matches("\\d+")) {
                continue;
            }
            descBuilder.append(tokens[i]).append(" ");
        }
        String descripcion = descBuilder.toString().trim();

        // Limitar estrictamente el largo de la descripción para que no pise al precio unitario (máximo 17 letras)
        if (descripcion.length() > 17) {
            descripcion = descripcion.substring(0, 15) + "..";
        }

        // Retorna una estructura tabular simétrica de 30 caracteres:
        // Cantidad (izq, ancho 3) + Descripción (izq, ancho 18) + V. Unitario (der, ancho 8)
        return String.format("%-3s %-17s %7s", cant, descripcion, vUnit);
    }
    /**
     * Función auxiliar para limpiar la cadena original del producto
     * y formatearla de forma compacta eliminando precios unitarios repetitivos
     * para que encaje perfectamente en las columnas del PDF.
     */
    private String formatearLineaProducto(String rawProduct) {
        if (rawProduct == null || rawProduct.isEmpty()) return "";

        // Simplificación de espacios y tabuladores internos
        String limpio = rawProduct.replaceAll("\\s+", " ").trim();

        // Intentar extraer la cantidad al principio
        String[] partes = limpio.split(" ", 2);
        if (partes.length < 2) return limpio;

        String cant = partes[0];
        String resto = partes[1];

        // Cortar el texto de la descripción si es excesivamente largo para la columna
        if (resto.length() > 22) {
            resto = resto.substring(0, 20) + "..";
        }

        // Formato tabulado fijo de ancho: Cantidad (4 caracteres) + Descripción
        return String.format("%-4s %-22s", cant, resto);
    }

    /**
     * Invoca el diálogo nativo del sistema operativo compartiendo de forma segura el archivo generado.
     */
    private void abrirMenuCompartir(File archivo) {
        Toast.makeText(this, "PDF guardado con éxito en Descargas", Toast.LENGTH_SHORT).show();

        Uri safeUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", archivo);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, safeUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Descargar y enviar PDF via:"));
    }
}