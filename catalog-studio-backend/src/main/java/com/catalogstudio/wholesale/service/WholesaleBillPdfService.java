package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.wholesale.entity.WholesaleOrder;
import com.catalogstudio.wholesale.entity.WholesaleOrderItem;
import com.catalogstudio.wholesale.entity.WholesaleStore;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * GST tax invoice PDF matching standard Indian retail / wholesale invoice layout
 * (TAX INVOICE header, bill-to + invoice meta, line table, amount in words, tax breakup, T&C / bank / signature).
 */
@Service
@RequiredArgsConstructor
public class WholesaleBillPdfService {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("dd-MMM-yy").withLocale(Locale.ENGLISH);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Color HEADER_BG = new Color(245, 245, 245);
    private static final Color LINE = new Color(60, 60, 60);

    private final StorageService storageService;

    public StoredBill generateAndStore(
            WholesaleStore store,
            WholesaleOrder order,
            List<WholesaleOrderItem> items
    ) {
        byte[] pdf = render(store, order, items);
        StorageService.StoredFile stored = storageService.storeBytes(pdf, "application/pdf");
        return new StoredBill(stored.publicUrl(), stored.storageKey());
    }

    private byte[] render(WholesaleStore store, WholesaleOrder order, List<WholesaleOrderItem> items) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 28, 28, 28, 28);
            PdfWriter.getInstance(document, out);
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font shop = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font tiny = FontFactory.getFont(FontFactory.HELVETICA, 7);

            // Top meta row
            PdfPTable top = new PdfPTable(new float[]{1f, 2f, 1f});
            top.setWidthPercentage(100);
            top.addCell(edge("Page No. 1 of 1", tiny, Element.ALIGN_LEFT));
            PdfPCell taxTitle = edge("TAX INVOICE", title, Element.ALIGN_CENTER);
            taxTitle.setPaddingBottom(4);
            top.addCell(taxTitle);
            top.addCell(edge("Original Copy", tiny, Element.ALIGN_RIGHT));
            document.add(top);

            // Seller block
            Paragraph sellerName = new Paragraph(nullToDash(store.getStoreName()), shop);
            sellerName.setAlignment(Element.ALIGN_CENTER);
            document.add(sellerName);
            Paragraph sellerAddr = new Paragraph(sellerAddress(store), small);
            sellerAddr.setAlignment(Element.ALIGN_CENTER);
            document.add(sellerAddr);
            String gstPan = "GSTIN - " + nullToDash(store.getGstin());
            if (StringUtils.hasText(store.getPan())) {
                gstPan += "  |  PAN - " + store.getPan().trim().toUpperCase(Locale.ROOT);
            }
            Paragraph ids = new Paragraph(gstPan, bold);
            ids.setAlignment(Element.ALIGN_CENTER);
            ids.setSpacingAfter(8);
            document.add(ids);

            Instant billInstant = order.getPaidAt() != null
                    ? order.getPaidAt()
                    : (order.getAcceptedAt() != null ? order.getAcceptedAt() : order.getPlacedAt());
            String invoiceDate = billInstant == null ? "-" : DAY.format(billInstant.atZone(IST));
            String dueDate = billInstant == null
                    ? "-"
                    : DAY.format(billInstant.atZone(IST).plusDays(15));

            // Bill to | Invoice meta
            PdfPTable meta = new PdfPTable(new float[]{1.15f, 0.85f});
            meta.setWidthPercentage(100);
            meta.setSpacingAfter(8);

            PdfPCell billTo = boxCell();
            billTo.addElement(new Paragraph("Customer Name: " + nullToDash(order.getBuyerName()), normal));
            billTo.addElement(new Paragraph(
                    "GSTIN: " + (StringUtils.hasText(order.getBuyerGstin()) ? order.getBuyerGstin().trim() : "-"),
                    normal));
            billTo.addElement(new Paragraph(
                    "Address: " + (StringUtils.hasText(order.getBuyerAddress())
                            ? order.getBuyerAddress().trim()
                            : join(", ", order.getBuyerMobile(), order.getBuyerEmail())),
                    normal));
            meta.addCell(billTo);

            PdfPCell inv = boxCell();
            inv.addElement(new Paragraph("Invoice Number: " + order.getOrderNumber(), normal));
            inv.addElement(new Paragraph("Invoice Date: " + invoiceDate, normal));
            inv.addElement(new Paragraph("Due Date: " + dueDate, normal));
            inv.addElement(new Paragraph(
                    "Place of Supply: " + placeOfSupply(store), normal));
            inv.addElement(new Paragraph("Reverse Charge: No", normal));
            meta.addCell(inv);
            document.add(meta);

            BigDecimal gstPercent = order.getGstPercent() == null
                    ? BigDecimal.ZERO
                    : order.getGstPercent().setScale(2, RoundingMode.HALF_UP);

            // Line items
            PdfPTable table = new PdfPTable(new float[]{0.45f, 2.4f, 0.9f, 0.7f, 0.55f, 0.95f, 0.7f, 0.65f, 1.0f});
            table.setWidthPercentage(100);
            for (String h : List.of(
                    "Sr.", "Item Description", "HSN/SAC", "Qty", "Unit", "List Price", "Disc.", "Tax %", "Amount (Rs)")) {
                table.addCell(headerCell(h, bold));
            }

            int sr = 1;
            BigDecimal goodsTotal = BigDecimal.ZERO;
            for (WholesaleOrderItem item : items) {
                BigDecimal unit = item.getAcceptedUnitPrice() != null
                        ? item.getAcceptedUnitPrice()
                        : item.getUnitPrice();
                BigDecimal line = unit.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                goodsTotal = goodsTotal.add(line);
                String desc = item.getProductTitle();
                String variant = join(" / ", item.getColorLabel(), item.getSizeLabel());
                if (StringUtils.hasText(variant)) {
                    desc = desc + " (" + variant + ")";
                }
                table.addCell(bodyCell(String.valueOf(sr++), normal, Element.ALIGN_CENTER));
                table.addCell(bodyCell(desc, normal, Element.ALIGN_LEFT));
                table.addCell(bodyCell(
                        StringUtils.hasText(item.getHsnSac()) ? item.getHsnSac().trim() : "-",
                        normal, Element.ALIGN_CENTER));
                table.addCell(bodyCell(String.format(Locale.ENGLISH, "%.2f", (double) item.getQuantity()),
                        normal, Element.ALIGN_RIGHT));
                table.addCell(bodyCell("Pcs.", normal, Element.ALIGN_CENTER));
                table.addCell(bodyCell(plain(unit), normal, Element.ALIGN_RIGHT));
                table.addCell(bodyCell("0.00 (%)", normal, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(plain(gstPercent), normal, Element.ALIGN_RIGHT));
                table.addCell(bodyCell(plain(line), normal, Element.ALIGN_RIGHT));
            }

            BigDecimal taxable = order.getTaxableAmount() != null ? order.getTaxableAmount() : nz(order.getSubtotal());
            BigDecimal cgst = nz(order.getCgstAmount());
            BigDecimal sgst = nz(order.getSgstAmount());
            BigDecimal grand = order.getGrandTotal() != null
                    ? order.getGrandTotal()
                    : taxable.add(cgst).add(sgst);

            PdfPCell grandLabel = bodyCell("Grand Total", bold, Element.ALIGN_RIGHT);
            grandLabel.setColspan(8);
            table.addCell(grandLabel);
            table.addCell(bodyCell(plain(grand), bold, Element.ALIGN_RIGHT));
            document.add(table);

            Paragraph words = new Paragraph(
                    "Rs. " + IndianAmountInWords.toWords(grand) + " only", bold);
            words.setSpacingBefore(6);
            words.setSpacingAfter(4);
            document.add(words);

            // Tax breakup
            String half = gstPercent.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP).toPlainString();
            Paragraph taxBreak = new Paragraph(String.format(Locale.ENGLISH,
                    "Sale @%s%% = %s, CGST@%s%% = %s, SGST@%s%% = %s  |  Total Sale = %s, Tax = %s, Cess = 0.00, Add. Cess = 0.00",
                    plain(gstPercent), plain(taxable),
                    half, plain(cgst),
                    half, plain(sgst),
                    plain(taxable), plain(cgst.add(sgst))),
                    small);
            taxBreak.setSpacingAfter(8);
            document.add(taxBreak);

            // Footer: Terms | Bank | Signature
            PdfPTable footer = new PdfPTable(new float[]{1.2f, 1.2f, 1f});
            footer.setWidthPercentage(100);

            PdfPCell terms = boxCell();
            terms.addElement(new Paragraph("Terms and Conditions", bold));
            terms.addElement(new Paragraph(termsText(store), tiny));
            footer.addCell(terms);

            PdfPCell bank = boxCell();
            bank.addElement(new Paragraph("Bank Details", bold));
            bank.addElement(new Paragraph("A/c No.: " + nullToDash(store.getBankAccountNumber()), tiny));
            bank.addElement(new Paragraph("Bank: " + nullToDash(store.getBankName()), tiny));
            bank.addElement(new Paragraph("IFSC: " + nullToDash(store.getBankIfsc()), tiny));
            bank.addElement(new Paragraph("Branch: " + nullToDash(store.getBankBranch()), tiny));
            bank.addElement(new Paragraph(
                    "Name: " + (StringUtils.hasText(store.getBankAccountName())
                            ? store.getBankAccountName().trim()
                            : nullToDash(store.getStoreName())),
                    tiny));
            footer.addCell(bank);

            PdfPCell sign = boxCell();
            sign.setMinimumHeight(72);
            sign.addElement(new Paragraph("For " + nullToDash(store.getStoreName()), bold));
            sign.addElement(new Paragraph(" "));
            sign.addElement(new Paragraph(" "));
            sign.addElement(new Paragraph("Signature", small));
            footer.addCell(sign);
            document.add(footer);

            if (StringUtils.hasText(order.getSupplierNote())) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Note: " + order.getSupplierNote().trim(), small));
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw ApiException.badRequest("Could not generate bill PDF");
        }
    }

    private static String termsText(WholesaleStore store) {
        if (StringUtils.hasText(store.getInvoiceTerms())) {
            return store.getInvoiceTerms().trim();
        }
        String jurisdiction = StringUtils.hasText(store.getCity()) ? store.getCity().trim() : "India";
        return "E. & O.E\n"
                + "1. Goods once sold will not be taken back.\n"
                + "2. Interest @ 18% p.a. will be charged if payment is not made within due date.\n"
                + "3. Subject to '" + jurisdiction + "' Jurisdiction only.";
    }

    private static String sellerAddress(WholesaleStore store) {
        String line = join(", ",
                store.getAddressLine(),
                store.getCity(),
                store.getState(),
                store.getPincode());
        return StringUtils.hasText(line) ? line : "India";
    }

    private static String placeOfSupply(WholesaleStore store) {
        String state = StringUtils.hasText(store.getState()) ? store.getState().trim() : "India";
        String code = gstStateCode(store.getGstin());
        return code == null ? state : code + " - " + state;
    }

    private static String gstStateCode(String gstin) {
        if (!StringUtils.hasText(gstin) || gstin.trim().length() < 2) {
            return null;
        }
        return gstin.trim().substring(0, 2);
    }

    private static PdfPCell edge(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(align);
        cell.setPadding(2);
        return cell;
    }

    private static PdfPCell boxCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.6f);
        cell.setPadding(6);
        return cell;
    }

    private static PdfPCell headerCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.5f);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private static PdfPCell bodyCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "-" : text, font));
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.4f);
        cell.setPadding(3);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    private static String plain(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nullToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(sep);
            }
            sb.append(part.trim());
        }
        return sb.toString();
    }

    public record StoredBill(String publicUrl, String storageKey) {}
}
