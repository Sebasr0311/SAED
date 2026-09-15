package com.edificio.admin.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GeneradorQR {
    private static final int ANCHO = 300;
    private static final int ALTO = 300;

    public String generarCodigoQR(String contenido) {
        return "QR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "-" +
               contenido.hashCode();
    }

    public BitMatrix crearMatrizQR(String contenido) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        return writer.encode(contenido, BarcodeFormat.QR_CODE, ANCHO, ALTO, hints);
    }

    public String generarCodigoAccesoTemporal(String identificacion) {
        long timestamp = System.currentTimeMillis();
        return "ACC-" + identificacion + "-" + timestamp;
    }

    public String generarCodigoInvitacion(String numeroDocumento, String idApartamento) {
        return "INV-" + numeroDocumento + "-" + idApartamento + "-" +
               System.currentTimeMillis();
    }
}