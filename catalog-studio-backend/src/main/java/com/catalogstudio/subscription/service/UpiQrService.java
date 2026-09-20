package com.catalogstudio.subscription.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class UpiQrService {

    public String upiUri(String upiId, String payeeName, BigDecimal amount, String note) {
        StringBuilder uri = new StringBuilder("upi://pay?pa=")
                .append(encode(upiId == null ? "" : upiId.trim()));
        if (StringUtils.hasText(payeeName)) {
            uri.append("&pn=").append(encode(payeeName.trim()));
        }
        if (amount != null && amount.signum() > 0) {
            uri.append("&am=").append(amount.stripTrailingZeros().toPlainString());
        }
        uri.append("&cu=INR");
        if (StringUtils.hasText(note)) {
            uri.append("&tn=").append(encode(note.trim()));
        }
        return uri.toString();
    }

    public String pngDataUrl(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 360, 360, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception ex) {
            log.warn("Could not generate UPI QR: {}", ex.getMessage());
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
