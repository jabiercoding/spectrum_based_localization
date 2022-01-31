package spectrum;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.but4reuse.adaptedmodel.AdaptedModel;
import org.but4reuse.adaptedmodel.Block;
import org.but4reuse.adaptedmodel.helpers.AdaptedModelHelper;
import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.javajdt.JavaJDTAdapter;
import org.but4reuse.artefactmodel.ArtefactModel;
import org.but4reuse.benchmarks.argoumlspl.utils.GenerateScenarioResources;
import org.but4reuse.benchmarks.argoumlspl.utils.TransformFLResultsToBenchFormat;
import org.but4reuse.block.identification.IBlockIdentification;
import org.but4reuse.fca.block.identification.FCABlockIdentification;
import org.but4reuse.feature.location.IFeatureLocation;
import org.but4reuse.feature.location.LocatedFeature;
import org.but4reuse.feature.location.impl.StrictFeatureSpecificFeatureLocation;
import org.but4reuse.featurelist.Feature;
import org.but4reuse.featurelist.FeatureList;
import org.but4reuse.featurelist.helpers.FeatureListHelper;

import metricsCalculation.MetricsCalculation;
import spectrum.utils.ConsoleProgressMonitor;
import utils.FileUtils;
import utils.ScenarioUtils;

public class Main_FCA_SFS {
	
	public static void main(String[] args) {

		File benchmarkFolder = new File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");

		File scenariosFolder = new File(benchmarkFolder, "scenarios");
		
		File outputFolder = new File("output");
		List<File> scenarios = ScenarioUtils.getAllScenariosOrderedByNumberOfVariants(scenariosFolder);
		
		Map<String,File> mapScenarioMetricsFile = new LinkedHashMap<String,File>();
		
		for (File scenario : scenarios) {
			System.out.println("Current scenario: " + scenario.getName());
			if (scenario.getName().contains("006")) {
				break;
			}
			// check if it was built
			if (!ScenarioUtils.isScenarioBuilt(scenario)) {
				System.out.println("Skip: The scenario variants were not derived.");
				continue;
			}

			// Get the artefact model and feature list of the scenario
			Object[] amAndFl = GenerateScenarioResources.createArtefactModelAndFeatureList(scenario, false);
			ArtefactModel am = (ArtefactModel) amAndFl[0];
			FeatureList fl = (FeatureList) amAndFl[1];

			// Expand the feature list with 2-wise feature interactions
			List<Feature> twoWise = FeatureListHelper.get2WiseFeatureInteractions(fl.getOwnedFeatures(), am);
			fl.getOwnedFeatures().addAll(twoWise);

			// Adapt the variants and create the adapted model
			IAdapter jdtAdapter = new JavaJDTAdapter();
			List<IAdapter> adapters = new ArrayList<IAdapter>();
			adapters.add(jdtAdapter);
			AdaptedModel adaptedModel = AdaptedModelHelper.adapt(am, adapters, new ConsoleProgressMonitor());

			// Get blocks
			IBlockIdentification blockIdentificationAlgo = new FCABlockIdentification();
			List<Block> blocks = blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(),
					new ConsoleProgressMonitor());
			adaptedModel.getOwnedBlocks().addAll(blocks);

			// Launch feature location
			IFeatureLocation featureLocationAlgo = new StrictFeatureSpecificFeatureLocation();
			List<LocatedFeature> flResult = featureLocationAlgo.locateFeatures(fl, adaptedModel,
					new ConsoleProgressMonitor());

			// Transform the results to the benchmark format
			System.out.println("Transforming to benchmark format");
			Map<String, Set<String>> benchmarkResults = TransformFLResultsToBenchFormat.transform(fl, adaptedModel,
					flResult);
			File resultsFolder = new File(outputFolder, scenario.getName());
			File locationFolder = new File(resultsFolder, "location");
			TransformFLResultsToBenchFormat.serializeResults(locationFolder, benchmarkResults);
			
			// Metrics calculation
			System.out.println("Calculating metrics");
			String results = MetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"), locationFolder);
			File resultsFile = new File(resultsFolder, "resultPrecisionRecall.csv");
			try {
				FileUtils.writeFile(resultsFile, results);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			mapScenarioMetricsFile.put(scenario.getName(), resultsFile);
		}
		
		createHTMLReport(outputFolder, mapScenarioMetricsFile);
	}

	/**
	 * Create html report
	 * @param outputFolder
	 * @param mapScenarioMetricsFile
	 */
	private static void createHTMLReport(File outputFolder, Map<String, File> mapScenarioMetricsFile) {
		
		// Copy the report template
		File report = new File(outputFolder, "report/report.html");
		FileUtils.copyFile(new File("reportTemplate/report.html"), report);
		FileUtils.copyFile(new File("reportTemplate/libs_js/Chart.bundle.js"), new File(outputFolder, "report/libs_js/Chart.bundle.js"));
		FileUtils.copyFile(new File("reportTemplate/libs_js/utils.js"), new File(outputFolder, "report/libs_js/utils.js"));
		
		// Get the positions of the parts to replace from the template
		List<String> lines = FileUtils.getLinesOfFile(report);
		int indexOfScenarioLabels = 0;
		int indexOfPrecision = 0;
		int indexOfRecall = 0;
		int indexOfF1 = 0;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.startsWith("labels:")) {
				indexOfScenarioLabels = i;
			} else if (line.startsWith("label: \"Precision\"")) {
				indexOfPrecision = i;
			} else if (line.startsWith("label: \"Recall\"")) {
				indexOfRecall = i;
			} else if (line.startsWith("label: \"F1\"")) {
				indexOfF1 = i;
			}
		}
		
		// Create the new lines
		StringBuffer scenariosLine = new StringBuffer();
		StringBuffer precisionLine = new StringBuffer();
		StringBuffer recallLine = new StringBuffer();
		StringBuffer f1Line = new StringBuffer();
		
		scenariosLine.append("labels: [");
		for (String scenario: mapScenarioMetricsFile.keySet()) {
			// short it
			scenario = scenario.replace("Scenario", "");
			scenario = scenario.replace("Variants", "");
			scenario = scenario.replace("Variant", "");
			scenariosLine.append("\"" + scenario + "\",");
		}
		// remove last comma
		scenariosLine.substring(0, scenariosLine.length()-2);
		scenariosLine.append("],\n");
		
		lines.remove(indexOfScenarioLabels);
		lines.add(indexOfScenarioLabels, scenariosLine.toString());
		
		precisionLine.append("label: \"Precision\", data: [");
		recallLine.append("label: \"Recall\", data: [");
		f1Line.append("label: \"F1\", data: [");
		
		for (String scenario: mapScenarioMetricsFile.keySet()) {
			File resultsFile = mapScenarioMetricsFile.get(scenario);
			List<String> resultsLines = FileUtils.getLinesOfFile(resultsFile);
			for (String resultLine: resultsLines) {
				if (resultLine.startsWith("Average,")) {
					String[] splitResultLine = resultLine.split(",");
					precisionLine.append(splitResultLine[1] + ",");
					recallLine.append(splitResultLine[2] + ",");
					f1Line.append(splitResultLine[3] + ",");
				}
			}
		}
		
		// remove last comma
		precisionLine.substring(0, precisionLine.length()-2);
		precisionLine.append("]\n");
		recallLine.substring(0, recallLine.length()-2);
		recallLine.append("]\n");
		f1Line.substring(0, f1Line.length()-2);
		f1Line.append("]\n");
		
		lines.remove(indexOfPrecision);
		lines.add(indexOfPrecision, precisionLine.toString());
		lines.remove(indexOfRecall);
		lines.add(indexOfRecall, recallLine.toString());
		lines.remove(indexOfF1);
		lines.add(indexOfF1, f1Line.toString());
				
		// Save the report
		StringBuffer newContent = new StringBuffer();
		for (String line: lines) {
			newContent.append(line);
			newContent.append("\n");
		}
		try {
			FileUtils.writeFile(report, newContent.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
