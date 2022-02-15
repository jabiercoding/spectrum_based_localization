package spectrum;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.but4reuse.adaptedmodel.AdaptedModel;
import org.but4reuse.adaptedmodel.Block;
import org.but4reuse.adaptedmodel.helpers.AdaptConcurrently;
import org.but4reuse.adaptedmodel.helpers.AdaptedModelHelper;
import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.jacoco.CoveredLineElement;
import org.but4reuse.adapters.jacoco.JacocoAdapter;
import org.but4reuse.adapters.javajdt.JavaJDTAdapter;
import org.but4reuse.adapters.javajdt.elements.CompilationUnitElement;
import org.but4reuse.adapters.javajdt.elements.FieldElement;
import org.but4reuse.adapters.javajdt.elements.ImportElement;
import org.but4reuse.adapters.javajdt.elements.MethodBodyElement;
import org.but4reuse.adapters.javajdt.elements.MethodElement;
import org.but4reuse.adapters.javajdt.utils.JDTElementUtils;
import org.but4reuse.artefactmodel.Artefact;
import org.but4reuse.artefactmodel.ArtefactModel;
import org.but4reuse.artefactmodel.ArtefactModelFactory;
import org.but4reuse.benchmarks.argoumlspl.utils.GenerateScenarioResources;
import org.but4reuse.benchmarks.argoumlspl.utils.TransformFLResultsToBenchFormat;
import org.but4reuse.block.identification.IBlockIdentification;
import org.but4reuse.block.identification.impl.SimilarElementsBlockIdentification;
import org.but4reuse.fca.block.identification.FCABlockIdentification;
import org.but4reuse.feature.location.IFeatureLocation;
import org.but4reuse.feature.location.LocatedFeature;
import org.but4reuse.feature.location.impl.StrictFeatureSpecificFeatureLocation;
import org.but4reuse.feature.location.spectrum.RankingMetrics;
import org.but4reuse.feature.location.spectrum.SpectrumBasedLocalization;
import org.but4reuse.featurelist.Feature;
import org.but4reuse.featurelist.FeatureList;
import org.but4reuse.featurelist.FeatureListFactory;
import org.but4reuse.featurelist.helpers.FeatureListHelper;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import fk.stardust.localizer.IFaultLocalizer;
import metricsCalculation.MetricsCalculation;
import spectrum.utils.ConsoleProgressMonitor;
import spectrum.utils.HTMLReportUtils;
import spectrum.utils.TypeLevelMetricsCalculation;
import utils.FeatureUtils;
import utils.FileUtils;
import utils.ScenarioUtils;

public class Main_HybridScenariosManual {

	public static void main(String[] args) {
		File benchmarkFolder = new File("C:\\ArgoUML-SPL\\ArgoUMLSPLBenchmark");
		// File benchmarkFolder = new
		// File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");

		File scenariosFolder = new File(benchmarkFolder, "scenarios");

		File originalVariantSrc = new File(benchmarkFolder,
				"scenarios/ScenarioOriginalVariant/variants/Original.config/src/org");

		File outputFolder = new File("output_HybridScenariosManual");
		List<File> scenarios = ScenarioUtils.getAllScenariosOrderedByNumberOfVariants(scenariosFolder);

		Map<String, File> mapScenarioMetricsFile = new LinkedHashMap<String, File>();
		Map<String, File> mapScenarioMetricsFileNaive = new LinkedHashMap<String, File>();
		Map<String, File> mapScenarioMetricsTypeLevelNaive = new LinkedHashMap<String, File>();
		Map<String, File> mapScenarioMetricsTypeLevelBench = new LinkedHashMap<String, File>();

		Map<String, Set<String>> benchmarkResultsHybridFinal = new LinkedHashMap<String, Set<String>>();
		Map<String, Set<String>> benchmarkResultsHybrid = new LinkedHashMap<String, Set<String>>();
		Map<String, Set<String>> benchmarkResultsStaticToRemove = new LinkedHashMap<String, Set<String>>();
		
		// create artefact model and feature list
		FeatureList featureList = FeatureListFactory.eINSTANCE.createFeatureList();
		ArtefactModel artefactModel = ArtefactModelFactory.eINSTANCE.createArtefactModel();
		featureList.setArtefactModel(artefactModel);

		File manualTraces = new File("execTraces/manual");
		for (File trace : manualTraces.listFiles()) {
			Artefact artefact = ArtefactModelFactory.eINSTANCE.createArtefact();
			artefact.setArtefactURI(trace.toURI().toString());
			String name = trace.getName().substring(0, trace.getName().length() - ".xml".length());
			artefact.setName(name);
			artefactModel.getOwnedArtefacts().add(artefact);

			Feature feature = FeatureListFactory.eINSTANCE.createFeature();
			feature.setName(name);
			feature.setId(name);
			feature.getImplementedInArtefacts().add(artefact);
			featureList.getOwnedFeatures().add(feature);
		}

		// Adapt the variants and create the adapted model
		IAdapter jacocoAdapter = new JacocoAdapter();
		List<IAdapter> adaptersJacoco = new ArrayList<IAdapter>();
		adaptersJacoco.add(jacocoAdapter);
		AdaptedModel adaptedModelJacoco = AdaptedModelHelper.adapt(artefactModel, adaptersJacoco,
				new ConsoleProgressMonitor());

		// Get blocks, one block per element
		SimilarElementsBlockIdentification blockIdentificationAlgoJacoco = new SimilarElementsBlockIdentification();
		List<Block> blocksJacoco = blockIdentificationAlgoJacoco
				.identifyBlocks(adaptedModelJacoco.getOwnedAdaptedArtefacts(), false, new ConsoleProgressMonitor());
		adaptedModelJacoco.getOwnedBlocks().addAll(blocksJacoco);

		// Launch feature location
		IFaultLocalizer<Block> wong2 = RankingMetrics.getRankingMetricByName("Wong2");
		SpectrumBasedLocalization featureLocationAlgoJacoco = new SpectrumBasedLocalization();
		List<LocatedFeature> flResultJacoco = featureLocationAlgoJacoco.locateFeatures(featureList, adaptedModelJacoco,
				wong2, 1.0, new ConsoleProgressMonitor());
		Map<String, Map<String, List<Integer>>> mapFeatureJavaLines = getResults(flResultJacoco);

		// adapt java source code of the original variant that was the one used
		// for the
		// traces
		System.out.println("Adapting Original source code with JDT");
		IAdapter jdtAdapterOriginalScenario = new JavaJDTAdapter();
		List<IElement> jdtElements = jdtAdapterOriginalScenario.adapt(originalVariantSrc.toURI(),
				new ConsoleProgressMonitor());

		System.out.println("Transform feature results in Lines to IElements");
		Map<String, List<IElement>> mapFeatureIElements = transformLinesToIElements(mapFeatureJavaLines, jdtElements);

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

			// dynamic approach
			FeatureUtils featureUtils = new FeatureUtils(scenario.getAbsolutePath(),
					new File(benchmarkFolder, "featuresInfo/features.txt").getAbsolutePath());

			Map<String, Set<String>> benchmarkResultsDynamic = new LinkedHashMap<String, Set<String>>();

			System.out.println("Adapting all variants in the scenario");
			Map<String, List<IElement>> mapVariantIElements = new LinkedHashMap<String, List<IElement>>();
			for (String configId : featureUtils.getConfigurationIds()) {
				File variantSrc = new File(featureUtils.getVariantFolderOfConfig(configId) + "/src/org/argouml");
				URI variantSrcURI = variantSrc.toURI();
				List<IElement> elements = jdtAdapterOriginalScenario.adapt(variantSrcURI, null);
				mapVariantIElements.put(configId, elements);
			}

			// for each feature
			for (String feature : mapFeatureIElements.keySet()) {
				System.out.println("Apply rules for feature: " + feature);
				List<IElement> jdtElementsJacoco = mapFeatureIElements.get(feature);

				Set<String> benchmarkResultsCurrentFeature = new LinkedHashSet<String>();

				List<String> configsNotContainingFeature = featureUtils.getConfigurationsNotContainingFeature(feature);
				List<String> configsContainingFeature = featureUtils.getConfigurationsContainingFeature(feature);

				System.out.println("Containing: " + configsContainingFeature);
				System.out.println("Not Containing: " + configsNotContainingFeature);

				System.out.println("Refining the results with the variants information");
				List<IElement> elementsToRemove = new ArrayList<>();

				// first rule
				// remove all jdt elements in common with variants that do
				// not contain the feature
				for (String configIdNotContaining : configsNotContainingFeature) {
					System.out.println("VARIANT: " + configIdNotContaining);
					List<IElement> elements = mapVariantIElements.get(configIdNotContaining);
					for (IElement iElement : jdtElementsJacoco) {
						if (elements.contains(iElement) && !elementsToRemove.contains(iElement)) {
							elementsToRemove.add(iElement);
						}
					}

				}
				System.out.println("SIZE elementsToRemove: " + elementsToRemove.size());
				System.out.println("SIZE jdtElementsJacoco before remove: " + jdtElementsJacoco.size());
				jdtElementsJacoco.removeAll(elementsToRemove);
				System.out.println("SIZE jdtElementsJacoco after remove: " + jdtElementsJacoco.size());

				Map<String, List<IElement>> elementsVariants = new HashMap<>();
				ArrayList<IElement> elementsInCommonAllVariantsWithFeature = new ArrayList<>();

				// second rule
				// get all the jdt elements of the variants that contain a
				// feature
				for (String configIdContaining : configsContainingFeature) {
					List<IElement> jdtElementsVariant = mapVariantIElements.get(configIdContaining);
					elementsVariants.put(configIdContaining, jdtElementsVariant);
				}

				Map<IElement, Integer> countElementsVariants = new HashMap<IElement, Integer>();

				for (List<IElement> elms : elementsVariants.values()) {
					for (IElement e : elms) {
						if (!countElementsVariants.containsKey(e)) {
							countElementsVariants.put(e, 1);
						} else {
							int count = countElementsVariants.get(e) + 1;
							countElementsVariants.remove(e);
							countElementsVariants.put(e, count);
						}
					}
				}

				for (Map.Entry<IElement, Integer> count : countElementsVariants.entrySet()) {
					if (count.getValue() == elementsVariants.size()) {
						elementsInCommonAllVariantsWithFeature.add(count.getKey());
					}
				}

				ArrayList<IElement> finaljdtElementsJacoco = new ArrayList<>();
				for (IElement iElement : jdtElementsJacoco) {
					if (elementsInCommonAllVariantsWithFeature.contains(iElement)
							&& !finaljdtElementsJacoco.contains(iElement)) {
						finaljdtElementsJacoco.add(iElement);
					}
				}

				System.out.println("SIZE elements in common all variants containing a feature: "
						+ elementsInCommonAllVariantsWithFeature.size());
				System.out.println("SIZE jdtElementsJacoco before second rule: " + jdtElementsJacoco.size());
				System.out.println("SIZE jdtElementsJacoco after second rule: " + finaljdtElementsJacoco.size());

				// compute results
				List<CompilationUnitElement> compilationUnitsAfterTwoRules = new ArrayList<CompilationUnitElement>();
				for (IElement element : finaljdtElementsJacoco) {
					CompilationUnitElement compUnit = null;
					if (element instanceof FieldElement) {
						compUnit = JDTElementUtils.getCompilationUnit((FieldElement) element);
					} else if (element instanceof MethodElement) {
						compUnit = JDTElementUtils.getCompilationUnit((MethodElement) element);
					} else if (element instanceof org.but4reuse.adapters.javajdt.elements.TypeElement) {
						compUnit = JDTElementUtils
								.getCompilationUnit((org.but4reuse.adapters.javajdt.elements.TypeElement) element);
					} else {
						System.out.println(element);
					}

					for (org.but4reuse.adapters.javajdt.elements.TypeElement typeElement : JDTElementUtils
							.getTypes(compUnit)) {
						// naive solution: adding a class-level localization
						// when
						// there is at least one line in the class.
						if (!compilationUnitsAfterTwoRules.contains(compUnit)) {
							TypeDeclaration type = (TypeDeclaration) typeElement.node;
							benchmarkResultsCurrentFeature
									.add(org.but4reuse.benchmarks.argoumlspl.utils.TraceIdUtils.getId(type));
							compilationUnitsAfterTwoRules.add(compUnit);
						}
					}

				}

				benchmarkResultsDynamic.put(feature, benchmarkResultsCurrentFeature);
			}
			// end dynamic approach -> results are benchmarkResultsDynamic

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
			// AdaptedModel adaptedModel = AdaptedModelHelper.adapt(am,
			// adapters, new ConsoleProgressMonitor());

			long start = System.currentTimeMillis();

			// Get blocks
			IBlockIdentification blockIdentificationAlgo = new FCABlockIdentification();
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
					} else if (e instanceof MethodBodyElement) {
						compUnit = JDTElementUtils.getCompilationUnit((MethodBodyElement) e);
					}

					if (compUnit != null) {
						for (org.but4reuse.adapters.javajdt.elements.TypeElement typeElement : JDTElementUtils
								.getTypes(compUnit)) {
							// naive solution: adding a class-level
							// localization
							// when
							// there is at least one line in the class.
							TypeDeclaration type = (TypeDeclaration) typeElement.node;
							String featureName = located.getFeature().getId();
							if (featureName.startsWith("I_"))
								featureName = featureName.replace("I_", "");

							// Create the name of the file based on the
							// features
							String fileName = "";

							// Check if the feature has interactions
							if (featureName.contains("_")) {
								Set<String> orderedNames = new TreeSet<>();
								// Get all the features that have interacted
								for (String interactedFeature : featureName.split("_")) {
									orderedNames.add(interactedFeature);
								}

								// Add the features found to a String with
								// its names
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

			
			// TODO add FLResults RQ2 when they are not there for HashMap naiveResults?
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
			
			
			
			
			// add the results from FLResults RQ2 (benchmarkResultsDynamic) in FLResults RQ1 (benchmarkResults) when they are not there
			for (Entry<String, Set<String>> dynamiclyLocatedFeature : benchmarkResultsDynamic.entrySet()) {
				Set<String> staticallyLocatedFeature = benchmarkResults.get(dynamiclyLocatedFeature.getKey());
				Set<String> dynamiclyLocatedElementsToADD = new HashSet<>();
				for (String locatedElement : dynamiclyLocatedFeature.getValue()) {
					if(!staticallyLocatedFeature.contains(locatedElement)){
						dynamiclyLocatedElementsToADD.add(locatedElement);
					}
				}
				Set<String> hybridLocatedElements = new HashSet<>();
				hybridLocatedElements.addAll(dynamiclyLocatedFeature.getValue());
				hybridLocatedElements.addAll(dynamiclyLocatedElementsToADD);
				benchmarkResultsHybrid.put(dynamiclyLocatedFeature.getKey(), hybridLocatedElements);
			}
			// end
			
			//If the results of RQ2 (benchmarkResultsDynamic) for a feature appear in other feature in RQ1 (benchmarkResults), we remove them in RQ1
			for (Entry<String, Set<String>> dynamiclyLocatedFeature : benchmarkResultsDynamic.entrySet()) {
				Set<String> staticallyLocatedElementsToREMOVE = new HashSet<>();
				for (Entry<String, Set<String>> locatedFeature : benchmarkResults.entrySet()) {
					//does not compare the elements of the same feature
					if(!locatedFeature.getKey().equals(dynamiclyLocatedFeature.getKey())){
						Set<String> locatedElements = locatedFeature.getValue();
						for (String locatedElement : dynamiclyLocatedFeature.getValue()) {
							if(locatedElements.contains(locatedElement)){
								staticallyLocatedElementsToREMOVE.add(locatedElement);
							}
						}
					}
					
					
				}
				benchmarkResultsStaticToRemove.put(dynamiclyLocatedFeature.getKey(),staticallyLocatedElementsToREMOVE);
			}
			// end
			
			//final results are inserted in benchmarkResultsHybridFinal
			for (Entry<String, Set<String>> hybrid : benchmarkResultsHybrid.entrySet()) {
				System.out.println("Feature: "+hybrid.getKey());
				System.out.println("Size hybrid before remove: "+hybrid.getValue().size());
				Set<String> elementsToREMOVE = benchmarkResultsStaticToRemove.get(hybrid.getKey());
				System.out.println("Size elements to remove: "+elementsToREMOVE.size());
				Set<String> finalSetElements = hybrid.getValue();
				finalSetElements.removeAll(elementsToREMOVE);
				System.out.println("Final set Elements size: "+finalSetElements.size());
				benchmarkResultsHybridFinal.put(hybrid.getKey(), finalSetElements);
			}
			
			File resultsFolder = new File(outputFolder, scenario.getName());
			File locationFolder = new File(resultsFolder, "location");
			TransformFLResultsToBenchFormat.serializeResults(locationFolder, benchmarkResultsHybridFinal);

			// Metrics calculation naive solution with benchmark ground
			// truth
			System.out.println("Calculating metrics naive solution");
			String resultsNaive = MetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"),
					resultsFolderNaive);
			File resultsFileNaive = new File(resultsFolderNaive, "resultPrecisionRecallBenchLevel.csv");
			try {
				FileUtils.writeFile(resultsFileNaive, resultsNaive);
			} catch (Exception e) {
				e.printStackTrace();
			}

			// Metrics calculation naive solution with type, i.e., class
			// level ground truth
			System.out.println("Calculating metrics naive solution");
			String resultsTypeLevelNaive = TypeLevelMetricsCalculation
					.getResults(new File(benchmarkFolder, "groundTruth"), resultsFolderNaive);
			File resultsFileTypeLevelNaive = new File(resultsFolderNaive, "resultPrecisionRecallTypeLevel.csv");
			try {
				FileUtils.writeFile(resultsFileTypeLevelNaive, resultsTypeLevelNaive);
			} catch (Exception e) {
				e.printStackTrace();
			}

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

			// update html report naive solution with benchmark groundtruth
			System.out.println("Update html report naive solution at the class level");
			mapScenarioMetricsFileNaive.put(scenario.getName(), resultsFileNaive);
			HTMLReportUtils.createReportNaiveResults(outputFolder, mapScenarioMetricsFileNaive, "reportNaive");

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

			// update html report naive solution with class level
			System.out.println("Update html report naive solution at the class level");
			mapScenarioMetricsTypeLevelNaive.put(scenario.getName(), resultsFileTypeLevelNaive);
			HTMLReportUtils.createReportNaiveResults(outputFolder, mapScenarioMetricsTypeLevelNaive,
					"reportTypeLevelNaive");
		}

	}

	/**
	 * Transform map of features and lines, to map of features and ielements
	 * 
	 * @param mapFeatureJavaLines
	 * @param jdtElements
	 * @return map feature ielements
	 */
	private static Map<String, List<IElement>> transformLinesToIElements(
			Map<String, Map<String, List<Integer>>> mapFeatureJavaLines, List<IElement> jdtElements) {

		// initialize the result
		Map<String, List<IElement>> mapFeatureIElements = new LinkedHashMap<String, List<IElement>>();

		// get all compilation unit elements
		List<CompilationUnitElement> compilationUnits = new ArrayList<CompilationUnitElement>();
		for (IElement element : jdtElements) {
			if (element instanceof CompilationUnitElement) {
				compilationUnits.add((CompilationUnitElement) element);
			}
		}

		for (String feature : mapFeatureJavaLines.keySet()) {
			ArrayList<IElement> jdtElementsJacoco = new ArrayList<>();

			System.out.println("Feature: " + feature);

			Map<String, List<Integer>> featureJava = mapFeatureJavaLines.get(feature);

			for (String javaFile : featureJava.keySet()) {
				CompilationUnitElement compUnit = getCompilationUnitElement(compilationUnits, javaFile);
				if (compUnit == null) {
					System.out.println("Not found: " + javaFile);
					continue;
				}
				CompilationUnit cu = (CompilationUnit) compUnit.node;

				// to remove repetitive jdt elements
				for (Integer line : featureJava.get(javaFile)) {
					// System.out.println(line);
					IElement element = getJDTElement(cu, line, javaFile);
					if (element != null && !jdtElementsJacoco.contains(element)) {
						jdtElementsJacoco.add(element);
					}
				}
			}
			mapFeatureIElements.put(feature, jdtElementsJacoco);
		}
		return mapFeatureIElements;
	}

	private static IElement getJDTElement(CompilationUnit cu, Integer lineNumber, String fileName) {
		// Visit the cu to find the element corresponding to a line
		TransformLinesToJDTElements visitor = new TransformLinesToJDTElements(cu, lineNumber, fileName);
		cu.accept(visitor);
		return visitor.e;
	}

	private static CompilationUnitElement getCompilationUnitElement(List<CompilationUnitElement> compilationUnits,
			String javaFile) {
		String cueFormat = javaFile.replaceAll("/", ".");
		int separator = cueFormat.substring(0, cueFormat.length() - ".java".length()).lastIndexOf('.');
		cueFormat = cueFormat.substring(0, separator) + " " + cueFormat.substring(separator + 1, cueFormat.length());
		for (CompilationUnitElement cu : compilationUnits) {
			if (cu.id.equals(cueFormat)) {
				return cu;
			}
		}
		return null;
	}

	/**
	 * Transform the results from locatedFeatures to a map
	 * 
	 * @param locatedFeatures
	 * @return map of feature, Java file, and list of lines
	 */
	public static Map<String, Map<String, List<Integer>>> getResults(List<LocatedFeature> locatedFeatures) {
		Map<String, Map<String, List<Integer>>> mapFeatureJavaLines = new LinkedHashMap<String, Map<String, List<Integer>>>();
		for (LocatedFeature located : locatedFeatures) {
			System.out.println(located.getFeature().getName() + " : "
					+ AdaptedModelHelper.getElementsOfBlock(located.getBlocks().get(0)));
			Map<String, List<Integer>> current = mapFeatureJavaLines.get(located.getFeature().getName());
			if (current == null) {
				current = new LinkedHashMap<String, List<Integer>>();
			}
			CoveredLineElement element = (CoveredLineElement) AdaptedModelHelper
					.getElementsOfBlock(located.getBlocks().get(0)).get(0);
			String javaFile = element.packageName + "/" + element.fileName;
			List<Integer> lines = current.get(javaFile);
			if (lines == null) {
				lines = new ArrayList<Integer>();
			}
			lines.add(element.lineNumber);
			current.put(javaFile, lines);
			mapFeatureJavaLines.put(located.getFeature().getName(), current);
		}
		return mapFeatureJavaLines;
	}

}
