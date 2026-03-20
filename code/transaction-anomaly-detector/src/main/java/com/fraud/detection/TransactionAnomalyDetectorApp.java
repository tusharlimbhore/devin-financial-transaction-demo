package com.fraud.detection;

import com.fraud.detection.model.AnomalyAlert;
import com.fraud.detection.model.Transaction;
import com.fraud.detection.service.TransactionAnalysisService;
import com.fraud.detection.util.CsvParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Main application entry point for the Transaction Anomaly Detector.
 *
 * <p>This application reads financial transaction data from a CSV file,
 * groups transactions by customer, and applies multiple anomaly detection
 * rules to identify suspicious patterns including:</p>
 * <ul>
 *   <li>Repeated high-value transactions in sequence</li>
 *   <li>TRANSFER followed by CASH_OUT (fund extraction pattern)</li>
 *   <li>TRANSFER → TRANSFER → CASH_OUT sequences (layering pattern)</li>
 *   <li>Sudden spikes in transaction amounts</li>
 *   <li>Sudden changes in transaction type</li>
 *   <li>Multiple high-value transactions in the same time window</li>
 * </ul>
 */
public class TransactionAnomalyDetectorApp {

    public static void main(String[] args) {
        String csvFilePath;

        if (args.length > 0) {
            csvFilePath = args[0];
        } else {
            // Default path relative to the project
            csvFilePath = "../Example1.csv";
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║         TRANSACTION ANOMALY SEQUENCE DETECTOR                ║");
        System.out.println("║         Financial Fraud Detection System                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Loading transactions from: " + csvFilePath);
        System.out.println();

        try {
            // Parse transactions from CSV
            List<Transaction> transactions = CsvParser.parseTransactions(csvFilePath);
            System.out.printf("Successfully loaded %d transactions.%n%n", transactions.size());

            // Run anomaly detection
            TransactionAnalysisService analysisService = new TransactionAnalysisService();
            Map<String, List<AnomalyAlert>> alerts = analysisService.analyzeTransactions(transactions);

            // Print summary report
            System.out.println(analysisService.generateSummaryReport(transactions, alerts));

            // Print detailed alerts per customer
            System.out.println("══════════════════════════════════════════════════════════════");
            System.out.println("                    DETAILED ALERT REPORT                     ");
            System.out.println("══════════════════════════════════════════════════════════════\n");

            if (alerts.isEmpty()) {
                System.out.println("  No anomalous transaction sequences detected.");
            } else {
                for (Map.Entry<String, List<AnomalyAlert>> entry : alerts.entrySet()) {
                    System.out.printf("┌─ Customer: %s (%d alert(s)) ─────────────────────────────%n",
                            entry.getKey(), entry.getValue().size());

                    for (AnomalyAlert alert : entry.getValue()) {
                        System.out.println(alert);
                        System.out.println();
                    }

                    System.out.println("└──────────────────────────────────────────────────────────\n");
                }
            }

            // Final statistics
            int totalAlerts = alerts.values().stream().mapToInt(List::size).sum();
            System.out.println("══════════════════════════════════════════════════════════════");
            System.out.printf("  Analysis complete. %d anomalies detected across %d customers.%n",
                    totalAlerts, alerts.size());
            System.out.println("══════════════════════════════════════════════════════════════");

        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
            System.err.println("Usage: java -jar transaction-anomaly-detector.jar <csv-file-path>");
            System.exit(1);
        }
    }
}
