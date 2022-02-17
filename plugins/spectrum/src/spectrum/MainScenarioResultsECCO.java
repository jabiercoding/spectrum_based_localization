package spectrum;

import java.io.File;

import spectrum.utils.TypeLevelMetricsCalculation;
import utils.FileUtils;

public class MainScenarioResultsECCO {

	public static void main(String[] args) {
		
		 File benchmarkFolder = new File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");
		 // As taken from https://github.com/jku-isse/SPLC2019-Challenge-ArgoUML-FeatureLocation
		 File eccoResults = new File("C:/git/SPLC2019-Challenge-ArgoUML-FeatureLocation/results");
		 File output = new File("output_typeLevelEcco");
		 output.mkdirs();
		
		for (File f : eccoResults.listFiles()) {
			File locationFolder = new File(f,"results");
			System.out.println("Calculating metrics type level");
			String resultsTypeLevel = TypeLevelMetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"), locationFolder);
			File resultsFileTypeLevel = new File(output, f.getName() + "_resultPrecisionRecallTypeLevel.csv");
			try {
				FileUtils.writeFile(resultsFileTypeLevel, resultsTypeLevel);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	
}
