package com.example.myapplication.service;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

// Importaciones corregidas para PDFBox Android
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;


public class PDFUtils {

    private static final String TAG = "PDFUtils"; // Etiqueta para los logs en Android

    /**
     * Extrae texto de un archivo PDF a partir de un InputStream.
     * Esta es la forma preferida para trabajar con archivos seleccionados por el usuario en Android
     * mediante un ContentResolver.
     *
     * @param inputStream El InputStream del archivo PDF.
     * @return El texto extraído del PDF.
     * @throws IOException Si ocurre un error durante la lectura del PDF.
     */
    public static String extraerTexto(InputStream inputStream) throws IOException {
        PDDocument document = null;
        try {
            document = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } finally {
            // Asegura que el documento PDF se cierre correctamente para liberar recursos
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error al cerrar el documento PDF: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Extrae texto de un archivo PDF a partir de un objeto File.
     * Aunque es posible, en Android es más común y seguro usar InputStream para URIs
     * obtenidos de selectores de archivos. Se mantiene por compatibilidad si es necesario.
     *
     * @param archivoPdf El objeto File del archivo PDF.
     * @return El texto extraído del PDF.
     * @throws IOException Si ocurre un error durante la lectura del PDF.
     */
    public static String extraerTexto(File archivoPdf) throws IOException {
        PDDocument document = null;
        try {
            document = PDDocument.load(archivoPdf);
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error al cerrar el documento PDF: " + e.getMessage(), e);
                }
            }
        }
    }
}