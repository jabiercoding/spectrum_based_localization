package spectrum;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.but4reuse.benchmarks.argoumlspl.utils.TransformFLResultsToBenchFormat;
import metricsCalculation.MetricsCalculation;
import spectrum.utils.HTMLReportUtils;
import spectrum.utils.TypeLevelMetricsCalculation;
import utils.FileUtils;
import utils.ScenarioUtils;

public class Main_HybridScenariosManual {

	public static void main(String[] args) {
		File benchmarkFolder = new File("/Users/brunomachado/eclipse-workspace/ArgoUMLSPLBenchmark");
		// File benchmarkFolder = new
		// File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");

		File scenariosFolder = new File(benchmarkFolder, "scenarios");

		File outputFolder = new File("output_HybridScenariosManual");
		List<File> scenarios = ScenarioUtils.getAllScenariosOrderedByNumberOfVariants(scenariosFolder);

		File traces = new File("execTraces/manual");

		Map<String, File> mapScenarioMetricsFile = new LinkedHashMap<String, File>();
		Map<String, File> mapScenarioMetricsTypeLevelBench = new LinkedHashMap<String, File>();

		// dynamic approach results benchmarkResultsDynamic
		Map<String, Map<String, Set<String>>> benchmarkResultsDynamic = DynamicScenarios
				.getScenarioDynamicResults(outputFolder, traces);

		// static approach results benchmarkResultsDynamic
		Map<String, Map<String, Set<String>>> benchmarkResultsStatic = StaticAnalysisScenarios
				.getScenarioStaticResults(outputFolder);

		for (File scenario : scenarios) {

			if (scenario.getName().contains("050")) {
				break;
			}

			if (scenario.getName().contains("Original")) {
				continue;
			}

			// add the results from FLResults RQ2 (benchmarkResultsDynamic) in
			// FLResults RQ3 (benchmarkResultsStatic) when they are not there
			for (Entry<String, Set<String>> dynamiclyLocatedFeature : benchmarkResultsDynamic.get(scenario.getName())
					.entrySet()) {
				for (String locatedElement : dynamiclyLocatedFeature.getValue()) {
					if (!benchmarkResultsStatic.get(scenario.getName()).get(dynamiclyLocatedFeature.getKey())
							.contains(locatedElement)) {
						benchmarkResultsStatic.get(scenario.getName()).get(dynamiclyLocatedFeature.getKey())
								.add(locatedElement);
					}
				}
			}
			// end

			// If the results of RQ2 (benchmarkResultsDynamic) for a feature
			// appear in other feature in RQ3 (benchmarkResultsStatic), we
			// remove them in RQ3
			Map<String, Set<String>> scenarioResultsDynamic = benchmarkResultsDynamic.get(scenario.getName());
			for (Map.Entry<String, Set<String>> dynamiclyLocatedFeature : scenarioResultsDynamic.entrySet()) {
				// analyze the elements of RQ2 for a feature				
				for (String locatedElement : dynamiclyLocatedFeature.getValue()) {
					// for each feature in RQ3
					for (Entry<String, Set<String>> locatedFeature : benchmarkResultsStatic.get(scenario.getName())
							.entrySet()) {
						// analyze the elements of the other features and not the one of the RQ2 in dynamiclyLocatedFeature
						if (!locatedFeature.getKey().equals(dynamiclyLocatedFeature.getKey())) {
							// remove the RQ2 element of a specific feature in RQ3 if it is in another feature of RQ3 elements
							if (locatedFeature.getValue().contains(locatedElement)) {
								benchmarkResultsStatic.get(scenario.getName()).get(dynamiclyLocatedFeature.getKey())
										.remove(locatedElement);
								break;//goes to the next element of RQ2
							}
						}
					}

				}
			}
			// end


			// final results are in benchmarkResultsStatic
			File resultsFolder = new File(outputFolder, scenario.getName());
			File locationFolder = new File(resultsFolder, "location");
			TransformFLResultsToBenchFormat.serializeResults(locationFolder,
					benchmarkResultsStatic.get(scenario.getName()));
			
			// Metrics calculation benchmark format with benchmark ground
			// truth
			System.out.println("Calculating metrics benchmark format");
			String results = MetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"), locationFolder);
			File resultsFile = new File(resultsFolder, "resultPrecisionRecallBenchLevel.csv");
			try {
				FileUtils.writeFile(resultsFile, results);
			} catch (Exception e) {
				e.printStackTrace();
			}

			// Metrics calculation benchmark format with type, i.e., class
			// level ground truth
			System.out.println("Calculating metrics type level");
			String resultsTypeLevel = TypeLevelMetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"),
					locationFolder);
			File resultsFileTypeLevel = new File(resultsFolder, "resultPrecisionRecallTypeLevel.csv");
			try {
				FileUtils.writeFile(resultsFileTypeLevel, resultsTypeLevel);
			} catch (Exception e) {
				e.printStackTrace();
			}

			// update html report benchmark format with benchmark
			// groundtruth
			System.out.println("Update html report benchmark formart");
			mapScenarioMetricsFile.put(scenario.getName(), resultsFile);
			HTMLReportUtils.create(outputFolder, mapScenarioMetricsFile);

			// update html report benchmark format with class level
			System.out.println("Update html report type level");
			mapScenarioMetricsTypeLevelBench.put(scenario.getName(), resultsFileTypeLevel);
			HTMLReportUtils.createReportNaiveResults(outputFolder, mapScenarioMetricsTypeLevelBench,
					"reportTypeLevelBench");

		}

	}

}
