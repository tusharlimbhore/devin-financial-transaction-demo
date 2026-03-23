package com.fraud.detection.service;

import com.fraud.detection.detector.AnomalyDetector;
import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.Transaction;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TransactionAnalysisServiceTest {

    private TransactionAnalysisService service;

    @Before
    public void setUp() {
        service = new TransactionAnalysisService();
    }

    private Transaction createTx(int step, String type, double amount, String nameOrig) {
        return new Transaction(step, type, amount, nameOrig, 10000, 10000 - amount,
                "C_DEST", 0, amount, 0, 0);
    }

    // --- getDetectors tests ---

    @Test
    public void testGetDetectorsReturns5() {
        List<AnomalyDetector> detectors = service.getDetectors();
        assertEquals(5, detectors.size());
    }

    @Test
    public void testGetDetectorsNotNull() {
        List<AnomalyDetector> detectors = service.getDetectors();
        assertNotNull(detectors);
        for (AnomalyDetector detector : detectors) {
            assertNotNull(detector);
            assertNotNull(detector.getDetectorName());
        }
    }

    @Test
    public void testDetectorNamesAreNonEmpty() {
        for (AnomalyDetector detector : service.getDetectors()) {
            assertTrue("Detector name should not be empty",
                    detector.getDetectorName().length() > 0);
        }
    }

    // --- generateSummaryReport tests ---

    @Test
    public void testGenerateSummaryReportWithNoAlerts() {
        List<Transaction> transactions = Arrays.asList(
                createTx(1, "PAYMENT", 500, "C1"),
                createTx(2, "PAYMENT", 600, "C2")
        );
        Map<String, List<AnomalyAlert>> alerts = new HashMap<>();

        String report = service.generateSummaryReport(transactions, alerts);

        assertNotNull(report);
        assertTrue(report.contains("SUMMARY REPORT"));
        assertTrue(report.contains("Total Transactions Analyzed : 2"));
        assertTrue(report.contains("Total Unique Customers      : 2"));
        assertTrue(report.contains("Customers with Anomalies    : 0"));
        assertTrue(report.contains("Total Anomaly Alerts        : 0"));
    }

    @Test
    public void testGenerateSummaryReportContainsOverviewSection() {
        List<Transaction> transactions = Collections.singletonList(
                createTx(1, "PAYMENT", 100, "C1"));
        Map<String, List<AnomalyAlert>> alerts = new HashMap<>();

        String report = service.generateSummaryReport(transactions, alerts);

        assertTrue(report.contains("Overview"));
    }

    @Test
    public void testGenerateSummaryReportContainsSeverityBreakdown() {
        List<Transaction> transactions = Collections.singletonList(
                createTx(1, "PAYMENT", 100, "C1"));
        Map<String, List<AnomalyAlert>> alerts = new HashMap<>();

        String report = service.generateSummaryReport(transactions, alerts);

        assertTrue(report.contains("Severity Breakdown"));
        assertTrue(report.contains("CRITICAL"));
        assertTrue(report.contains("HIGH"));
        assertTrue(report.contains("MEDIUM"));
        assertTrue(report.contains("LOW"));
    }

    @Test
    public void testGenerateSummaryReportContainsActiveDetectors() {
        List<Transaction> transactions = Collections.singletonList(
                createTx(1, "PAYMENT", 100, "C1"));
        Map<String, List<AnomalyAlert>> alerts = new HashMap<>();

        String report = service.generateSummaryReport(transactions, alerts);

        assertTrue(report.contains("Active Detectors"));
        // Should list all 5 detector names
        for (AnomalyDetector detector : service.getDetectors()) {
            assertTrue("Report should contain detector: " + detector.getDetectorName(),
                    report.contains(detector.getDetectorName()));
        }
    }

    @Test
    public void testGenerateSummaryReportWithAlerts() {
        Transaction tx1 = createTx(1, "TRANSFER", 50000, "C1");
        Transaction tx2 = createTx(2, "CASH_OUT", 49000, "C1");
        List<Transaction> transactions = Arrays.asList(tx1, tx2);

        AnomalyAlert alert = new AnomalyAlert("C1",
                AnomalyAlert.AnomalyType.TRANSFER_THEN_CASHOUT,
                AnomalyAlert.Severity.CRITICAL,
                "Transfer then cash-out detected",
                Arrays.asList(tx1, tx2));

        Map<String, List<AnomalyAlert>> alerts = new HashMap<>();
        alerts.put("C1", Collections.singletonList(alert));

        String report = service.generateSummaryReport(transactions, alerts);

        assertTrue(report.contains("Total Transactions Analyzed : 2"));
        assertTrue(report.contains("Customers with Anomalies    : 1"));
        assertTrue(report.contains("Total Anomaly Alerts        : 1"));
        assertTrue(report.contains("CRITICAL : 1"));
        assertTrue(report.contains("Anomaly Type Breakdown"));
        assertTrue(report.contains("Transfer Followed by Cash-Out"));
    }

    @Test
    public void testGenerateSummaryReportSeverityCounts() {
        Transaction tx = createTx(1, "PAYMENT", 15000, "C1");
        List<Transaction> transactions = Collections.singletonList(tx);

        AnomalyAlert critAlert = new AnomalyAlert("C1",
                AnomalyAlert.AnomalyType.TRANSFER_THEN_CASHOUT,
                AnomalyAlert.Severity.CRITICAL,
                "critical issue",
                Collections.singletonList(tx));
        AnomalyAlert highAlert = new AnomalyAlert("C1",
                AnomalyAlert.AnomalyType.SUDDEN_AMOUNT_SPIKE,
                AnomalyAlert.Severity.HIGH,
                "high issue",
                Collections.singletonList(tx));
        AnomalyAlert medAlert = new AnomalyAlert("C1",
                AnomalyAlert.AnomalyType.SUDDEN_TYPE_CHANGE,
                AnomalyAlert.Severity.MEDIUM,
                "medium issue",
                Collections.singletonList(tx));

        Map<String, List<AnomalyAlert>> alerts = new HashMap<>();
        alerts.put("C1", Arrays.asList(critAlert, highAlert, medAlert));

        String report = service.generateSummaryReport(transactions, alerts);

        assertTrue(report.contains("CRITICAL : 1"));
        assertTrue(report.contains("HIGH     : 1"));
        assertTrue(report.contains("MEDIUM   : 1"));
        assertTrue(report.contains("LOW      : 0"));
        assertTrue(report.contains("Total Anomaly Alerts        : 3"));
    }

    @Test
    public void testGenerateSummaryReportEmptyTransactions() {
        List<Transaction> transactions = Collections.emptyList();
        Map<String, List<AnomalyAlert>> alerts = new HashMap<>();

        String report = service.generateSummaryReport(transactions, alerts);

        assertTrue(report.contains("Total Transactions Analyzed : 0"));
        assertTrue(report.contains("Total Unique Customers      : 0"));
    }

    @Test
    public void testGenerateSummaryReportMultipleCustomers() {
        List<Transaction> transactions = Arrays.asList(
                createTx(1, "PAYMENT", 100, "C1"),
                createTx(2, "PAYMENT", 200, "C2"),
                createTx(3, "PAYMENT", 300, "C3")
        );
        Map<String, List<AnomalyAlert>> alerts = new HashMap<>();

        String report = service.generateSummaryReport(transactions, alerts);

        assertTrue(report.contains("Total Transactions Analyzed : 3"));
        assertTrue(report.contains("Total Unique Customers      : 3"));
    }
}
