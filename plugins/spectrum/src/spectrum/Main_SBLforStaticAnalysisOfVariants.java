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
import org.but4reuse.block.identification.impl.SimilarElementsBlockIdentification;
import org.but4reuse.feature.location.LocatedFeature;
import org.but4reuse.feature.location.spectrum.RankingMetrics;
import org.but4reuse.feature.location.spectrum.SpectrumBasedLocalization;
import org.but4reuse.featurelist.Feature;
import org.but4reuse.featurelist.FeatureList;
import org.but4reuse.featurelist.helpers.FeatureListHelper;

import fk.stardust.localizer.IFaultLocalizer;
import metricsCalculation.MetricsCalculation;
import spectrum.utils.ConsoleProgressMonitor;
import spectrum.utils.HTMLReportUtils;
import utils.FileUtils;
import utils.ScenarioUtils;

public class Main_SBLforStaticAnalysisOfVariants {

	public static void main(String[] args) {

		File benchmarkFolder = new File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");

		File scenariosFolder = new File(benchmarkFolder, "scenarios");

		File outputFolder = new File("output_SBL_Variants_Static");
		List<File> scenarios = ScenarioUtils.getAllScenariosOrderedByNumberOfVariants(scenariosFolder);

		Map<String, File> mapScenarioMetricsFile = new LinkedHashMap<String, File>();

		for (File scenario : scenarios) {
			
			if (scenario.getName().contains("Original")) {
				continue;
			}
			
			System.out.println("Current scenario: " + scenario.getName());

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

			long start = System.currentTimeMillis();
			
			// Get blocks
			// Using similar elements we will get one block for each element
			SimilarElementsBlockIdentification blockIdentificationAlgo = new SimilarElementsBlockIdentification();
			List<Block> blocks = blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(), false,
					new ConsoleProgressMonitor());
			adaptedModel.getOwnedBlocks().addAll(blocks);

			// Launch feature location
			IFaultLocalizer<Block> wong2 = RankingMetrics.getRankingMetricByName("Wong2");
			SpectrumBasedLocalization featureLocationAlgo = new SpectrumBasedLocalization();
			List<LocatedFeature> flResult = featureLocationAlgo.locateFeatures(fl, adaptedModel, wong2, 1.0,
					new ConsoleProgressMonitor());
			
			long finish = System.currentTimeMillis();
			long elapsedTime = finish - start;
			System.out.println("BI+FL Time in seconds: " + elapsedTime / 1000.0);

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
			resultsFile.getParentFile().mkdirs();
			try {
				FileUtils.writeFile(resultsFile, results);
			} catch (Exception e) {
				e.printStackTrace();
			}

			System.out.println("Update html report");
			mapScenarioMetricsFile.put(scenario.getName(), resultsFile);
			HTMLReportUtils.create(outputFolder, mapScenarioMetricsFile);
		}
	}
}
