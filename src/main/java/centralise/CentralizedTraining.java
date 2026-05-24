package centralise;

import com.example.peersimdjl.CnnModel;
import com.example.peersimdjl.DatasetPreprocessor;
import com.example.peersimdjl.NeuralNetworkModel;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CentralizedTraining {

    public static void main(String[] args) throws Exception {
        String modelType = args.length > 0 ? args[0].trim().toLowerCase() : "mlp";
        Path projectRoot = locateProjectRoot();
        Path defaultDataPath = projectRoot.resolve(Paths.get("data", "covtype", "covtype_200k.csv"));
        Path dataPath = args.length > 1 ? resolvePath(projectRoot, args[1]) : defaultDataPath;
        int epochs = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        double learningRate = args.length > 3 ? Double.parseDouble(args[3]) : 0.01;

        if (!Files.exists(dataPath)) {
            throw new IOException("Dataset introuvable: " + dataPath);
        }

        Path outputPath = projectRoot.resolve(Paths.get("centralise", "results.txt"));

        List<String[]> rawRows = loadRawCsv(dataPath);
        DatasetPreprocessor.Result preprocessed = DatasetPreprocessor.preprocess(rawRows, true);
        if (preprocessed.data.length == 0) {
            throw new IOException("Aucune donnée exploitable trouvée dans: " + dataPath);
        }

        int inputDim = Math.max(1, preprocessed.columnCount - 1);
        double[][] data = preprocessed.data;
        // Nombre de lignes utilisables après prétraitement
        int usableRows = data.length;
        System.out.println("Lignes utilisables pour l'entraînement: " + usableRows);

        double[][] features = new double[data.length][inputDim];
        double[][] labels = new double[data.length][1];
        for (int i = 0; i < data.length; i++) {
            double[] row = data[i];
            System.arraycopy(row, 0, features[i], 0, inputDim);
            labels[i][0] = row[inputDim];
        }

        List<Float> epochLosses = new ArrayList<>();
        double accuracy = 0.0;
        long start = System.currentTimeMillis();

        try {
            if ("cnn".equals(modelType)) {
                accuracy = trainWithCnn(features, labels, epochs, learningRate, inputDim, epochLosses);
            } else {
                accuracy = trainWithMlp(features, labels, epochs, learningRate, inputDim, epochLosses);
            }
        } finally {
            long end = System.currentTimeMillis();
            double durationSec = (end - start) / 1000.0;
            double avgLoss = epochLosses.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);

            Files.createDirectories(outputPath.getParent());
            try (PrintWriter out = new PrintWriter(new FileWriter(outputPath.toFile()))) {
                out.println("Model type: " + modelType);
                out.println("Epochs: " + epochs);
                out.println("Learning rate: " + learningRate);
                out.println("Input dim: " + inputDim);
                    out.println("Usable rows: " + usableRows);
                out.println("Total duration (s): " + durationSec);
                out.println("Average loss per epoch: " + avgLoss);
                out.println("Accuracy: " + accuracy);
                out.println("Losses: " + epochLosses);
            }

            System.out.println("Results saved to: " + outputPath);
            System.out.println("Total duration (s): " + durationSec);
            System.out.println("Average loss per epoch: " + avgLoss);
            System.out.println("Accuracy: " + accuracy);
        }
    }

    private static double trainWithMlp(double[][] features, double[][] labels, int epochs, double learningRate,
                                       int inputDim, List<Float> epochLosses) {
        NeuralNetworkModel model = new NeuralNetworkModel(learningRate, inputDim);
        try {
            for (int epoch = 0; epoch < epochs; epoch++) {
                float loss = model.trainBatch(features, labels);
                epochLosses.add(loss);
                System.out.println("[MLP] Epoch " + (epoch + 1) + "/" + epochs + " - Loss: " + loss);
            }
            double accuracy = model.evaluate(features, labels);
            System.out.println("[MLP] Accuracy: " + accuracy);
            return accuracy;
        } finally {
            model.close();
        }
    }

    private static double trainWithCnn(double[][] features, double[][] labels, int epochs, double learningRate,
                                       int inputDim, List<Float> epochLosses) {
        CnnModel model = new CnnModel(inputDim, (float) learningRate);
        try {
            for (int epoch = 0; epoch < epochs; epoch++) {
                float loss = model.trainBatch(features, labels);
                epochLosses.add(loss);
                System.out.println("[CNN] Epoch " + (epoch + 1) + "/" + epochs + " - Loss: " + loss);
            }
            double accuracy = model.evaluate(features, labels);
            System.out.println("[CNN] Accuracy: " + accuracy);
            return accuracy;
        } finally {
            model.close();
        }
    }

    private static List<String[]> loadRawCsv(Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                rows.add(line.split(","));
            }
        }
        return rows;
    }

    private static Path resolvePath(Path projectRoot, String value) {
        Path candidate = Paths.get(value);
        if (!candidate.isAbsolute()) {
            candidate = projectRoot.resolve(candidate);
        }
        return candidate.normalize();
    }

    private static Path locateProjectRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("pom.xml"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("pom.xml"))) {
            return parent;
        }
        return current;
    }
}