import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fraud Risk Scoring Module (Java 8 compatible)
 *
 * Computes risk scores (0-100) for financial transactions and assigns
 * risk categories (LOW, MEDIUM, HIGH) based on configurable risk factors.
 *
 * Risk Guidelines:
 * 1. Transactions above 10,000 are high risk.
 * 2. CASH_OUT and TRANSFER are higher risk transaction types.
 * 3. Transactions to new or previously unseen destination accounts are risky.
 * 4. Rapid sequence of transactions from same account increases risk.
 * 5. Fraudulent transactions often involve high amounts followed by cash-out.
 *
 * Risk Levels:
 *   LOW:    score < 40
 *   MEDIUM: score between 40 and 70
 *   HIGH:   score > 70
 */
public class FraudRiskScoring {

    // --- Configuration ---
    private static final double HIGH_AMOUNT_THRESHOLD = 10000.0;
    private static final Set<String> HIGH_RISK_TYPES = new HashSet<>(Arrays.asList("CASH_OUT", "TRANSFER"));

    // Score weights for each risk factor
    private static final double WEIGHT_AMOUNT = 25.0;
    private static final double WEIGHT_TYPE = 20.0;
    private static final double WEIGHT_NEW_DEST = 15.0;
    private static final double WEIGHT_RAPID_SEQUENCE = 20.0;
    private static final double WEIGHT_HIGH_AMOUNT_CASHOUT = 20.0;

    // Risk category thresholds
    private static final double LOW_THRESHOLD = 40.0;
    private static final double HIGH_THRESHOLD = 70.0;

    // --- Transaction representation ---
    static class Transaction {
        int step;
        String type;
        double amount;
        String nameOrig;
        double oldbalanceOrg;
        double newbalanceOrig;
        String nameDest;
        double oldbalanceDest;
        double newbalanceDest;
        int isFraud;
        int isFlaggedFraud;

        Transaction(String[] fields) {
            this.step = Integer.parseInt(fields[0].trim());
            this.type = fields[1].trim();
            this.amount = Double.parseDouble(fields[2].trim());
            this.nameOrig = fields[3].trim();
            this.oldbalanceOrg = Double.parseDouble(fields[4].trim());
            this.newbalanceOrig = Double.parseDouble(fields[5].trim());
            this.nameDest = fields[6].trim();
            this.oldbalanceDest = Double.parseDouble(fields[7].trim());
            this.newbalanceDest = Double.parseDouble(fields[8].trim());
            this.isFraud = Integer.parseInt(fields[9].trim());
            this.isFlaggedFraud = Integer.parseInt(fields[10].trim());
        }
    }

    // --- Scored transaction with risk details ---
    static class ScoredTransaction {
        Transaction txn;
        double riskScore;
        String riskCategory;
        double amountRisk;
        double typeRisk;
        double newDestinationRisk;
        double rapidSequenceRisk;
        double highAmountCashoutRisk;

        ScoredTransaction(Transaction txn) {
            this.txn = txn;
        }
    }

    // --- Risk scoring functions ---

    /**
     * Score based on transaction amount.
     * 0 for amounts <= 0, linearly increasing up to HIGH_AMOUNT_THRESHOLD,
     * full score for amounts >= HIGH_AMOUNT_THRESHOLD.
     */
    static double scoreAmount(double amount) {
        if (amount <= 0) {
            return 0.0;
        }
        if (amount >= HIGH_AMOUNT_THRESHOLD) {
            return WEIGHT_AMOUNT;
        }
        return round2(WEIGHT_AMOUNT * (amount / HIGH_AMOUNT_THRESHOLD));
    }

    /**
     * Score based on transaction type.
     * CASH_OUT and TRANSFER receive full weight; other types get 0.
     */
    static double scoreType(String txnType) {
        if (HIGH_RISK_TYPES.contains(txnType)) {
            return WEIGHT_TYPE;
        }
        return 0.0;
    }

    /**
     * Score based on whether the destination is new/previously unseen.
     * A destination is considered 'new' if this transaction is the first
     * time we see that destination account in the dataset.
     */
    static double scoreNewDestination(int txnIndex, String dest, Map<String, Integer> firstSeenMap) {
        Integer firstIdx = firstSeenMap.get(dest);
        if (firstIdx != null && firstIdx == txnIndex) {
            return WEIGHT_NEW_DEST;
        }
        return 0.0;
    }

    /**
     * Score based on rapid sequences from the same origin account.
     * If the same origin account has multiple transactions in the same step
     * or consecutive steps, the risk increases.
     */
    static double scoreRapidSequence(int txnIndex, String origin, int currentStep,
                                     Map<String, List<int[]>> accountHistories) {
        List<int[]> history = accountHistories.get(origin);
        if (history == null || history.size() <= 1) {
            return 0.0;
        }

        int rapidCount = 0;
        for (int[] entry : history) {
            int idx = entry[0];
            int step = entry[1];
            if (idx != txnIndex && Math.abs(step - currentStep) <= 1) {
                rapidCount++;
            }
        }

        if (rapidCount == 0) {
            return 0.0;
        }

        // Scale: 1 nearby txn -> 50% weight, 2 -> 75%, 3+ -> 100%
        double fraction = Math.min(1.0, 0.5 + 0.25 * (rapidCount - 1));
        return round2(WEIGHT_RAPID_SEQUENCE * fraction);
    }

    /**
     * Score based on high-amount-followed-by-cash-out pattern.
     * If the origin account has a preceding high-amount transaction and the
     * current transaction is a CASH_OUT, this is a strong fraud indicator.
     */
    static double scoreHighAmountCashoutPattern(Transaction txn, int txnIndex, String origin,
                                                 List<Transaction> allTransactions,
                                                 Map<String, List<int[]>> accountHistories) {
        if (!"CASH_OUT".equals(txn.type)) {
            return 0.0;
        }

        List<int[]> history = accountHistories.get(origin);

        // Check if any prior transaction from this account had a high amount
        boolean hasPriorHighAmount = false;
        if (history != null) {
            for (int[] entry : history) {
                int idx = entry[0];
                if (idx < txnIndex && allTransactions.get(idx).amount > HIGH_AMOUNT_THRESHOLD) {
                    hasPriorHighAmount = true;
                    break;
                }
            }
        }

        // Also consider if the cash-out itself is high amount
        boolean isHighAmount = txn.amount > HIGH_AMOUNT_THRESHOLD;

        if (hasPriorHighAmount && isHighAmount) {
            return WEIGHT_HIGH_AMOUNT_CASHOUT;
        } else if (hasPriorHighAmount || isHighAmount) {
            return round2(WEIGHT_HIGH_AMOUNT_CASHOUT * 0.6);
        } else {
            return round2(WEIGHT_HIGH_AMOUNT_CASHOUT * 0.2);
        }
    }

    /**
     * Classify a risk score into LOW, MEDIUM, or HIGH.
     */
    static String classifyRisk(double score) {
        if (score < LOW_THRESHOLD) {
            return "LOW";
        } else if (score <= HIGH_THRESHOLD) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    // --- Data loading and preprocessing ---

    static List<Transaction> loadTransactions(String filepath) throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(filepath));
        try {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] fields = line.split(",", -1);
                if (fields.length >= 11) {
                    transactions.add(new Transaction(fields));
                }
            }
        } finally {
            reader.close();
        }
        return transactions;
    }

    /**
     * Build per-origin-account transaction histories.
     * Each entry is int[] { index, step }.
     */
    static Map<String, List<int[]>> buildAccountHistories(List<Transaction> transactions) {
        Map<String, List<int[]>> histories = new HashMap<>();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction txn = transactions.get(i);
            List<int[]> list = histories.get(txn.nameOrig);
            if (list == null) {
                list = new ArrayList<>();
                histories.put(txn.nameOrig, list);
            }
            list.add(new int[]{i, txn.step});
        }
        return histories;
    }

    /**
     * Track the first occurrence index for each destination account.
     */
    static Map<String, Integer> buildFirstSeenDestinations(List<Transaction> transactions) {
        Map<String, Integer> firstSeen = new HashMap<>();
        for (int i = 0; i < transactions.size(); i++) {
            String dest = transactions.get(i).nameDest;
            if (!firstSeen.containsKey(dest)) {
                firstSeen.put(dest, i);
            }
        }
        return firstSeen;
    }

    // --- Core scoring ---

    static List<ScoredTransaction> computeRiskScores(List<Transaction> transactions) {
        Map<String, Integer> firstSeenMap = buildFirstSeenDestinations(transactions);
        Map<String, List<int[]>> accountHistories = buildAccountHistories(transactions);

        List<ScoredTransaction> results = new ArrayList<>();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction txn = transactions.get(i);
            ScoredTransaction scored = new ScoredTransaction(txn);

            scored.amountRisk = scoreAmount(txn.amount);
            scored.typeRisk = scoreType(txn.type);
            scored.newDestinationRisk = scoreNewDestination(i, txn.nameDest, firstSeenMap);
            scored.rapidSequenceRisk = scoreRapidSequence(i, txn.nameOrig, txn.step, accountHistories);
            scored.highAmountCashoutRisk = scoreHighAmountCashoutPattern(
                    txn, i, txn.nameOrig, transactions, accountHistories);

            double total = scored.amountRisk + scored.typeRisk + scored.newDestinationRisk
                    + scored.rapidSequenceRisk + scored.highAmountCashoutRisk;
            scored.riskScore = Math.min(100.0, round2(total));
            scored.riskCategory = classifyRisk(scored.riskScore);

            results.add(scored);
        }
        return results;
    }

    // --- Report generation ---

    static void generateRiskReport(List<ScoredTransaction> scoredTransactions, String outputPath)
            throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));
        try {
            writer.write("step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,"
                    + "oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud,"
                    + "risk_score,risk_category,amount_risk,type_risk,"
                    + "new_destination_risk,rapid_sequence_risk,high_amount_cashout_risk");
            writer.newLine();

            for (ScoredTransaction s : scoredTransactions) {
                Transaction t = s.txn;
                StringBuilder sb = new StringBuilder();
                sb.append(t.step).append(",");
                sb.append(t.type).append(",");
                sb.append(t.amount).append(",");
                sb.append(t.nameOrig).append(",");
                sb.append(t.oldbalanceOrg).append(",");
                sb.append(t.newbalanceOrig).append(",");
                sb.append(t.nameDest).append(",");
                sb.append(t.oldbalanceDest).append(",");
                sb.append(t.newbalanceDest).append(",");
                sb.append(t.isFraud).append(",");
                sb.append(t.isFlaggedFraud).append(",");
                sb.append(s.riskScore).append(",");
                sb.append(s.riskCategory).append(",");
                sb.append(s.amountRisk).append(",");
                sb.append(s.typeRisk).append(",");
                sb.append(s.newDestinationRisk).append(",");
                sb.append(s.rapidSequenceRisk).append(",");
                sb.append(s.highAmountCashoutRisk);
                writer.write(sb.toString());
                writer.newLine();
            }
        } finally {
            writer.close();
        }
    }

    static void printSummary(List<ScoredTransaction> scoredTransactions) {
        int countLow = 0, countMedium = 0, countHigh = 0;
        double totalScore = 0.0;
        double maxScore = 0.0;
        double minScore = 100.0;

        for (ScoredTransaction s : scoredTransactions) {
            switch (s.riskCategory) {
                case "LOW":    countLow++;    break;
                case "MEDIUM": countMedium++; break;
                case "HIGH":   countHigh++;   break;
            }
            totalScore += s.riskScore;
            maxScore = Math.max(maxScore, s.riskScore);
            minScore = Math.min(minScore, s.riskScore);
        }

        int total = scoredTransactions.size();
        double avgScore = total > 0 ? round2(totalScore / total) : 0.0;

        System.out.println("============================================================");
        System.out.println("FRAUD RISK SCORING REPORT SUMMARY");
        System.out.println("============================================================");
        System.out.println("Total transactions analyzed: " + total);
        System.out.println("Average risk score: " + avgScore);
        System.out.println("Min risk score: " + minScore);
        System.out.println("Max risk score: " + maxScore);
        System.out.println("------------------------------------------------------------");
        System.out.println("Risk Distribution:");
        System.out.printf("  LOW  (score < %.0f):  %d (%.1f%%)%n", LOW_THRESHOLD, countLow,
                100.0 * countLow / total);
        System.out.printf("  MEDIUM (score %.0f-%.0f): %d (%.1f%%)%n", LOW_THRESHOLD, HIGH_THRESHOLD,
                countMedium, 100.0 * countMedium / total);
        System.out.printf("  HIGH (score > %.0f): %d (%.1f%%)%n", HIGH_THRESHOLD, countHigh,
                100.0 * countHigh / total);
        System.out.println("------------------------------------------------------------");

        // Sort by risk score descending for top 10
        List<ScoredTransaction> sorted = new ArrayList<>(scoredTransactions);
        Collections.sort(sorted, new Comparator<ScoredTransaction>() {
            @Override
            public int compare(ScoredTransaction a, ScoredTransaction b) {
                return Double.compare(b.riskScore, a.riskScore);
            }
        });

        System.out.println();
        System.out.println("Top 10 Highest Risk Transactions:");
        System.out.printf("%-8s%-12s%14s%-18s%-18s%8s%-10s%n",
                "Index", "Type", "Amount", "Origin", "Dest", "Score", "Category");
        System.out.println("----------------------------------------------------------------------------------------");
        int limit = Math.min(10, sorted.size());
        for (int i = 0; i < limit; i++) {
            ScoredTransaction s = sorted.get(i);
            System.out.printf("%-8d%-12s%,14.2f%-18s%-18s%8.2f%-10s%n",
                    i + 1, s.txn.type, s.txn.amount, s.txn.nameOrig,
                    s.txn.nameDest, s.riskScore, s.riskCategory);
        }
        System.out.println("============================================================");
    }

    // --- Utility ---

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // --- Main ---

    public static void main(String[] args) {
        // Default to looking for Example1.csv in the same directory as the class file,
        // falling back to the current working directory.
        String scriptDir = System.getProperty("user.dir");
        try {
            File classLocation = new File(FraudRiskScoring.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (classLocation.isFile()) {
                scriptDir = classLocation.getParent();
            } else {
                scriptDir = classLocation.getPath();
            }
        } catch (Exception e) {
            // Fall back to current working directory
        }

        String inputPath = scriptDir + File.separator + "Example1.csv";
        String outputPath = scriptDir + File.separator + "transaction_risk_report.csv";

        if (args.length > 0) {
            inputPath = args[0];
        }
        if (args.length > 1) {
            outputPath = args[1];
        }

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found: " + inputPath);
            System.exit(1);
        }

        try {
            System.out.println("Loading transactions from: " + inputPath);
            List<Transaction> transactions = loadTransactions(inputPath);
            System.out.println("Loaded " + transactions.size() + " transactions.");

            System.out.println("Computing risk scores...");
            List<ScoredTransaction> scored = computeRiskScores(transactions);

            System.out.println("Generating risk report: " + outputPath);
            generateRiskReport(scored, outputPath);

            printSummary(scored);
            System.out.println();
            System.out.println("Detailed report saved to: " + outputPath);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
