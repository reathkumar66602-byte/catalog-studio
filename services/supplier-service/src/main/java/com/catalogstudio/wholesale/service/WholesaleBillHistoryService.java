package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supplier.client.UserDirectoryClient;
import com.catalogstudio.supplier.client.UserSnapshot;
import com.catalogstudio.wholesale.dto.BillHistoryResponse;
import com.catalogstudio.wholesale.dto.BillHistoryResponse.BillRow;
import com.catalogstudio.wholesale.dto.PageResponse;
import com.catalogstudio.wholesale.entity.WholesaleOrder;
import com.catalogstudio.wholesale.entity.WholesaleStore;
import com.catalogstudio.wholesale.repository.WholesaleOrderRepository;
import com.catalogstudio.wholesale.repository.WholesaleStoreRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WholesaleBillHistoryService {

    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<WholesaleOrder.Status> BILL_STATUSES =
            EnumSet.of(WholesaleOrder.Status.ACCEPTED, WholesaleOrder.Status.FULFILLED);

    private final WholesaleOrderRepository orderRepository;
    private final WholesaleStoreRepository storeRepository;
    private final UserDirectoryClient userDirectoryClient;
    private final WholesaleAccessService accessService;

    @Transactional(readOnly = true)
    public BillHistoryResponse buyerBills(Long buyerUserId, String fromDate, String toDate, int page, int size) {
        accessService.requireLiveStockBuyer(buyerUserId);
        DateRange range = parseRange(fromDate, toDate);
        PageRequest pageable = pageable(page, size);
        Page<WholesaleOrder> result = orderRepository.findBuyerBills(
                buyerUserId, BILL_STATUSES, range.from(), range.toExclusive(), pageable);
        List<WholesaleOrder> all = orderRepository.listBuyerBills(
                buyerUserId, BILL_STATUSES, range.from(), range.toExclusive());
        List<BillRow> allRows = toBuyerRows(all);
        List<BillRow> pageRows = toBuyerRows(result.getContent());
        return BillHistoryResponse.of(
                range.fromDate(),
                range.toDate(),
                null,
                allRows,
                PageResponse.of(result, pageRows));
    }

    @Transactional(readOnly = true)
    public byte[] buyerBillsExcel(Long buyerUserId, String fromDate, String toDate) {
        accessService.requireLiveStockBuyer(buyerUserId);
        DateRange range = parseRange(fromDate, toDate);
        List<BillRow> rows = toBuyerRows(orderRepository.listBuyerBills(
                buyerUserId, BILL_STATUSES, range.from(), range.toExclusive()));
        return writeExcel(
                "My bills",
                new String[] {
                    "Bill date", "Invoice / order", "Store", "Status", "Source",
                    "Subtotal", "Discount", "Taxable", "CGST", "SGST", "GST %", "Grand total", "Bill PDF"
                },
                rows,
                false);
    }

    @Transactional(readOnly = true)
    public BillHistoryResponse supplierBills(Long supplierUserId, String fromDate, String toDate, int page, int size) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        DateRange range = parseRange(fromDate, toDate);
        PageRequest pageable = pageable(page, size);
        Page<WholesaleOrder> result = orderRepository.findStoreBills(
                store.getId(), BILL_STATUSES, range.from(), range.toExclusive(), pageable);
        List<WholesaleOrder> all = orderRepository.listStoreBills(
                store.getId(), BILL_STATUSES, range.from(), range.toExclusive());
        List<BillRow> allRows = toSupplierRows(all);
        List<BillRow> pageRows = toSupplierRows(result.getContent());
        return BillHistoryResponse.of(
                range.fromDate(),
                range.toDate(),
                store.getStoreName(),
                allRows,
                PageResponse.of(result, pageRows));
    }

    @Transactional(readOnly = true)
    public byte[] supplierBillsExcel(Long supplierUserId, String fromDate, String toDate) {
        WholesaleStore store = requireOwnStore(supplierUserId);
        DateRange range = parseRange(fromDate, toDate);
        List<BillRow> rows = toSupplierRows(orderRepository.listStoreBills(
                store.getId(), BILL_STATUSES, range.from(), range.toExclusive()));
        return writeExcel(
                store.getStoreName() + " bills",
                new String[] {
                    "Bill date", "Invoice / order", "Buyer name", "Buyer email", "Buyer mobile",
                    "Status", "Source", "Subtotal", "Discount", "Taxable", "CGST", "SGST", "GST %",
                    "Grand total", "Bill PDF"
                },
                rows,
                true);
    }

    private List<BillRow> toBuyerRows(List<WholesaleOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, WholesaleStore> stores = storeRepository
                .findByIdIn(orders.stream().map(WholesaleOrder::getStoreId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(WholesaleStore::getId, s -> s));
        return orders.stream().map(order -> {
            WholesaleStore store = stores.get(order.getStoreId());
            return row(order, store == null ? null : store.getStoreName(),
                    store == null ? null : store.getStoreName(),
                    store == null ? null : store.getEmail(),
                    store == null ? null : store.getPhone());
        }).toList();
    }

    private List<BillRow> toSupplierRows(List<WholesaleOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> buyerIds = orders.stream()
                .map(WholesaleOrder::getBuyerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserSnapshot> buyers = buyerIds.isEmpty()
                ? Map.of()
                : userDirectoryClient.findByIds(buyerIds);
        return orders.stream().map(order -> {
            String name = order.getBuyerName();
            String email = order.getBuyerEmail();
            String mobile = order.getBuyerMobile();
            if (order.getBuyerUserId() != null) {
                UserSnapshot user = buyers.get(order.getBuyerUserId());
                if (user != null) {
                    if (!StringUtils.hasText(name)) {
                        name = user.name();
                    }
                    if (!StringUtils.hasText(email)) {
                        email = user.email();
                    }
                    if (!StringUtils.hasText(mobile)) {
                        mobile = user.mobile();
                    }
                }
            }
            return row(order, name, null, email, mobile);
        }).toList();
    }

    private static BillRow row(
            WholesaleOrder order,
            String counterpartyName,
            String storeName,
            String counterpartyEmail,
            String counterpartyMobile
    ) {
        Instant billDate = order.getPaidAt() != null
                ? order.getPaidAt()
                : (order.getAcceptedAt() != null ? order.getAcceptedAt() : order.getPlacedAt());
        BigDecimal grand = order.getGrandTotal() != null
                ? order.getGrandTotal()
                : (order.getAcceptedTotal() != null ? order.getAcceptedTotal() : nz(order.getSubtotal()));
        return new BillRow(
                order.getUuid(),
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getSource() == null ? WholesaleOrder.Source.ONLINE.name() : order.getSource().name(),
                billDate,
                counterpartyName,
                counterpartyEmail,
                counterpartyMobile,
                storeName,
                nz(order.getSubtotal()),
                nz(order.getDiscountAmount()),
                order.getTaxableAmount() != null ? order.getTaxableAmount() : nz(order.getSubtotal()),
                nz(order.getCgstAmount()),
                nz(order.getSgstAmount()),
                order.getGstPercent(),
                grand,
                order.getBillPdfUrl());
    }

    private byte[] writeExcel(String sheetName, String[] headers, List<BillRow> rows, boolean supplierView) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (BillRow bill : rows) {
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(formatDay(bill.billDate()));
                row.createCell(c++).setCellValue(nullToEmpty(bill.orderNumber()));
                if (supplierView) {
                    row.createCell(c++).setCellValue(nullToEmpty(bill.counterpartyName()));
                    row.createCell(c++).setCellValue(nullToEmpty(bill.counterpartyEmail()));
                    row.createCell(c++).setCellValue(nullToEmpty(bill.counterpartyMobile()));
                } else {
                    row.createCell(c++).setCellValue(nullToEmpty(bill.storeName()));
                }
                row.createCell(c++).setCellValue(nullToEmpty(bill.status()));
                row.createCell(c++).setCellValue(nullToEmpty(bill.source()));
                setMoney(row.createCell(c++), bill.subtotal());
                setMoney(row.createCell(c++), bill.discountAmount());
                setMoney(row.createCell(c++), bill.taxableAmount());
                setMoney(row.createCell(c++), bill.cgstAmount());
                setMoney(row.createCell(c++), bill.sgstAmount());
                setMoney(row.createCell(c++), bill.gstPercent());
                setMoney(row.createCell(c++), bill.grandTotal());
                row.createCell(c).setCellValue(nullToEmpty(bill.billPdfUrl()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("Could not build Excel export: " + ex.getMessage());
        }
    }

    private WholesaleStore requireOwnStore(Long userId) {
        accessService.requireSupplier(userId);
        return storeRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("You do not have a wholesale store"));
    }

    static DateRange parseRange(String fromDate, String toDate) {
        LocalDate today = LocalDate.now(INDIA);
        LocalDate from = StringUtils.hasText(fromDate) ? LocalDate.parse(fromDate.trim()) : today.withDayOfMonth(1);
        LocalDate to = StringUtils.hasText(toDate) ? LocalDate.parse(toDate.trim()) : today;
        if (to.isBefore(from)) {
            throw ApiException.badRequest("End date must be on or after start date");
        }
        if (from.plusYears(2).isBefore(to)) {
            throw ApiException.badRequest("Date range cannot exceed 2 years");
        }
        Instant fromInstant = from.atStartOfDay(INDIA).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(INDIA).toInstant();
        return new DateRange(from.format(DAY), to.format(DAY), fromInstant, toExclusive);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                WholesaleProductService.clampSize(size),
                Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id")));
    }

    private static void setMoney(Cell cell, BigDecimal value) {
        cell.setCellValue(value == null ? 0d : value.doubleValue());
    }

    private static String formatDay(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DAY.format(instant.atZone(INDIA));
    }

    private static String safeSheetName(String name) {
        String cleaned = (name == null ? "Bills" : name).replaceAll("[\\\\/*?\\[\\]:]", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "Bills";
        }
        return cleaned.substring(0, Math.min(31, cleaned.length()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record DateRange(String fromDate, String toDate, Instant from, Instant toExclusive) {}
}
