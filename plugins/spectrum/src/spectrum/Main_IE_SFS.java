package spectrum;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.but4reuse.adaptedmodel.AdaptedModel;
import org.but4reuse.adaptedmodel.Block;
import org.but4reuse.adaptedmodel.helpers.AdaptConcurrently;
import org.but4reuse.adaptedmodel.helpers.AdaptedModelHelper;
import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.javajdt.JavaJDTAdapter;
import org.but4reuse.adapters.javajdt.elements.CompilationUnitElement;
import org.but4reuse.adapters.javajdt.elements.FieldElement;
import org.but4reuse.adapters.javajdt.elements.ImportElement;
import org.but4reuse.adapters.javajdt.elements.MethodBodyElement;
import org.but4reuse.adapters.javajdt.elements.MethodElement;
import org.but4reuse.adapters.javajdt.utils.JDTElementUtils;
import org.but4reuse.artefactmodel.ArtefactModel;
import org.but4reuse.benchmarks.argoumlspl.utils.GenerateScenarioResources;
import org.but4reuse.benchmarks.argoumlspl.utils.TransformFLResultsToBenchFormat;
import org.but4reuse.block.identification.IBlockIdentification;
import org.but4reuse.block.identification.impl.IntersectionsBlockIdentification;
import org.but4reuse.feature.location.IFeatureLocation;
import org.but4reuse.feature.location.LocatedFeature;
import org.but4reuse.feature.location.impl.StrictFeatureSpecificFeatureLocation;
import org.but4reuse.featurelist.Feature;
import org.but4reuse.featurelist.FeatureList;
import org.but4reuse.featurelist.helpers.FeatureListHelper;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import metricsCalculation.MetricsCalculation;
import spectrum.utils.ConsoleProgressMonitor;
import spectrum.utils.HTMLReportUtils;
import spectrum.utils.TypeLevelMetricsCalculation;
import utils.FileUtils;
import utils.ScenarioUtils;

public class Main_IE_SFS {

	public static void main(String[] args) {

		// File benchmarkFolder = new File("C:\\ArgoUML-SPL\\ArgoUMLSPLBenchmark");
		 File benchmarkFolder = new
		 File("/Users/brunomachado/eclipse-workspace/ArgoUMLSPLBenchmark");

		File scenariosFolder = new File(benchmarkFolder, "scenarios");

		File outputFolder = new File("output_IE_SFS");
		List<File> scenarios = ScenarioUtils.getAllScenariosOrderedByNumberOfVariants(scenariosFolder);
		
		
		Map<String, File> mapScenarioMetricsFile = new LinkedHashMap<String, File>();
		Map<String, File> mapScenarioMetricsFileNaive = new LinkedHashMap<String, File>();
		Map<String, File> mapScenarioMetricsTypeLevelNaive = new LinkedHashMap<String, File>();
		Map<String, File> mapScenarioMetricsTypeLevelBench = new LinkedHashMap<String, File>();

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
			// Start Preparation timer
			long startPreparation = System.currentTimeMillis();
			
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
			AdaptedModel adaptedModel = AdaptConcurrently.adaptConcurrently(am, adapters, new ConsoleProgressMonitor());
			//AdaptedModel adaptedModel = AdaptedModelHelper.adapt(am, adapters, new ConsoleProgressMonitor());

			// Stop Timer for Preparation
			long finishPreparation = System.currentTimeMillis();
			long elapsedTimePreparation = finishPreparation - startPreparation;
						
			long start = System.currentTimeMillis();

			// Get blocks
			IBlockIdentification blockIdentificationAlgo = new IntersectionsBlockIdentification();
			List<Block> blocks = blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(),
					new ConsoleProgressMonitor());
			adaptedModel.getOwnedBlocks().addAll(blocks);

			// Launch feature location
			IFeatureLocation featureLocationAlgo = new StrictFeatureSpecificFeatureLocation();
			List<LocatedFeature> flResult = featureLocationAlgo.locateFeatures(fl, adaptedModel,
					new ConsoleProgressMonitor());

			long finish = System.currentTimeMillis();
			long elapsedTime = finish - start;
			System.out.println("BI+FL Time in seconds: " + elapsedTime / 1000.0);

			// Transform the results with the naive technique
			Multimap<String, String> resultsFeatures = ArrayListMultimap.create();
			for (LocatedFeature located : flResult) {
				System.out.println(located.getFeature().getName());
				List<IElement> elements = AdaptedModelHelper.getElementsOfBlock(located.getBlocks().get(0));
				for (IElement e : elements) {
					CompilationUnitElement compUnit = null;
					if (e instanceof FieldElement) {
						compUnit = JDTElementUtils.getCompilationUnit((FieldElement) e);
					} else if (e instanceof MethodElement) {
						compUnit = JDTElementUtils.getCompilationUnit((MethodElement) e);
					} else if (e instanceof org.but4reuse.adapters.javajdt.elements.TypeElement) {
						compUnit = JDTElementUtils
								.getCompilationUnit((org.but4reuse.adapters.javajdt.elements.TypeElement) e);
					} else if (e instanceof org.but4reuse.adapters.javajdt.elements.ImportElement) {
						 compUnit = JDTElementUtils.getCompilationUnit((ImportElement) e);
						//System.out.println(e);
					} else if (e instanceof MethodBodyElement) {
						compUnit = JDTElementUtils.getCompilationUnit((MethodBodyElement) e);
						//System.out.println(e);
					} else if (e instanceof CompilationUnitElement) {
						// compUnit =
						// JDTElementUtils.getCompilationUnit((CompilationUnitElement)
						// e);
						//System.out.println(e);
					} else {
						//System.out.println(e);
					}

					if (compUnit != null) {
						for (org.but4reuse.adapters.javajdt.elements.TypeElement typeElement : JDTElementUtils
								.getTypes(compUnit)) {
							// naive solution: adding a class-level localization
							// when
							// there is at least one line in the class.
							TypeDeclaration type = (TypeDeclaration) typeElement.node;
							String featureName = located.getFeature().getId();
							if (featureName.startsWith("I_"))
								featureName = featureName.replace("I_", "");
							
							// Create the name of the file based on the features
							String fileName = "";
							
							// Check if the feature has interactions
							if (featureName.contains("_")) {
								Set<String> orderedNames = new TreeSet<>();
								// Get all the features that have interacted
								for (String interactedFeature : featureName.split("_")) {
									orderedNames.add(interactedFeature);
								}

								// Add the features found to a String with its names
								for (String orderedFeatures : orderedNames) {
									fileName += orderedFeatures + "_and_";
								}
								// remove last "and"
								fileName = fileName.substring(0, fileName.length() - "_and_".length());
								// Check if it is a negation feature
							} else if (located.getFeature().getNegationFeatureOf() != null) {
								fileName = "not_" + featureName;
							} else {
								fileName = featureName;
							}
							
							
							resultsFeatures.put(fileName,
									org.but4reuse.benchmarks.argoumlspl.utils.TraceIdUtils.getId(type));
						}
					}
				}
			}

			Map<String, Set<String>> naiveResults = new HashMap<>();
			for (String feature : resultsFeatures.keySet()) {
				naiveResults.put(feature, Sets.newHashSet(resultsFeatures.get(feature)));
			}

			// results using naive technique at the class level
			File outputFolderNaive = new File(outputFolder, scenario.getName());
			File resultsFolderNaive = new File(outputFolderNaive, "locationNaive");
			TransformFLResultsToBenchFormat.serializeResults(resultsFolderNaive, naiveResults);

			// Transform the results to the benchmark format
			System.out.println("Transforming to benchmark format");
			Map<String, Set<String>> benchmarkResults = TransformFLResultsToBenchFormat.transform(fl, adaptedModel,
					flResult);
			File resultsFolder = new File(outputFolder, scenario.getName());
			File locationFolder = new File(resultsFolder, "location");
			TransformFLResultsToBenchFormat.serializeResults(locationFolder, benchmarkResults);

			// Metrics calculation naive solution with benchmark ground truth
			System.out.println("Calculating metrics naive solution");
			String resultsNaive = MetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"),
					resultsFolderNaive);
			File resultsFileNaive = new File(resultsFolderNaive, "resultPrecisionRecallBenchLevel.csv");
			try {
				FileUtils.writeFile(resultsFileNaive, resultsNaive);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			
			// Metrics calculation naive solution with type, i.e., class level ground truth
			System.out.println("Calculating metrics naive solution");
			String resultsTypeLevelNaive = TypeLevelMetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"),
								resultsFolderNaive);
			File resultsFileTypeLevelNaive = new File(resultsFolderNaive, "resultPrecisionRecallTypeLevel.csv");
			try {
				FileUtils.writeFile(resultsFileTypeLevelNaive, resultsTypeLevelNaive);
			} catch (Exception e) {
				e.printStackTrace();
			}
			

			// Metrics calculation benchmark format with benchmark ground truth
			System.out.println("Calculating metrics benchmark format");
			String results = MetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"), locationFolder);
			File resultsFile = new File(resultsFolder, "resultPrecisionRecallBenchLevel.csv");
			try {
				FileUtils.writeFile(resultsFile, results);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			// Metrics calculation benchmark format with type, i.e., class level ground truth
			System.out.println("Calculating metrics type level");
			String resultsTypeLevel = TypeLevelMetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"), locationFolder);
			File resultsFileTypeLevel = new File(resultsFolder, "resultPrecisionRecallTypeLevel.csv");
			try {
				FileUtils.writeFile(resultsFileTypeLevel, resultsTypeLevel);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			// Save tracked runtimes to a file for each specific scenario 
			File runtimeTrackerFile = new File(new File(outputFolder, "report"), scenario.getName()+ "_times.txt");
			try {
				FileUtils.writeFile(runtimeTrackerFile, Long.toString(elapsedTimePreparation) + "\n" + Long.toString(elapsedTime));
			} catch(Exception e) {
				e.printStackTrace();
			}
			

			// update html report naive solution with benchmark groundtruth
			System.out.println("Update html report naive solution at the class level");
			mapScenarioMetricsFileNaive.put(scenario.getName(), resultsFileNaive);
			HTMLReportUtils.createReportNaiveResults(outputFolder, mapScenarioMetricsFileNaive, "reportNaive");

			// update html report benchmark format with benchmark groundtruth
			System.out.println("Update html report benchmark formart");
			mapScenarioMetricsFile.put(scenario.getName(), resultsFile);
			HTMLReportUtils.create(outputFolder, mapScenarioMetricsFile);
			
			// update html report benchmark format with class level
			System.out.println("Update html report type level");
			mapScenarioMetricsTypeLevelBench.put(scenario.getName(), resultsFileTypeLevel);
			HTMLReportUtils.createReportNaiveResults(outputFolder, mapScenarioMetricsTypeLevelBench, "reportTypeLevelBench");
			
			// update html report naive solution with class level
			System.out.println("Update html report naive solution at the class level");
			mapScenarioMetricsTypeLevelNaive.put(scenario.getName(), resultsFileTypeLevelNaive);
			HTMLReportUtils.createReportNaiveResults(outputFolder, mapScenarioMetricsTypeLevelNaive, "reportTypeLevelNaive");
		}

	}

}
