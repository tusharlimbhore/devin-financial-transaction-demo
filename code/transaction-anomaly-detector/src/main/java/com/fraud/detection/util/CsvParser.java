package com.fraud.detection.util;

import com.fraud.detection.model.Transaction;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to parse transaction CSV files.
 */
public class CsvParser {

    /**
     * Parses a CSV file and returns a list of Transaction objects.
     *
     * @param filePath path to the CSV file
     * @return list of parsed transactions
     * @throws IOException if file cannot be read
     */
    public static List<Transaction> parseTransactions(String filePath) throws IOException {
        List<Transaction> transactions = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine(); // skip header
            if (line == null) {
                return transactions;
            }

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                Transaction transaction = parseLine(line);
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
        }

        return transactions;
    }

    private static Transaction parseLine(String line) {
        try {
            String[] fields = line.split(",");
            if (fields.length < 11) {
                return null;
            }

            int step = Integer.parseInt(fields[0].trim());
            String type = fields[1].trim();
            double amount = Double.parseDouble(fields[2].trim());
            String nameOrig = fields[3].trim();
            double oldBalanceOrig = Double.parseDouble(fields[4].trim());
            double newBalanceOrig = Double.parseDouble(fields[5].trim());
            String nameDest = fields[6].trim();
            double oldBalanceDest = Double.parseDouble(fields[7].trim());
            double newBalanceDest = Double.parseDouble(fields[8].trim());
            int isFraud = Integer.parseInt(fields[9].trim());
            int isFlaggedFraud = Integer.parseInt(fields[10].trim());

            return new Transaction(step, type, amount, nameOrig, oldBalanceOrig, newBalanceOrig,
                    nameDest, oldBalanceDest, newBalanceDest, isFraud, isFlaggedFraud);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Skipping malformed line: " + line);
            return null;
        }
    }
}
