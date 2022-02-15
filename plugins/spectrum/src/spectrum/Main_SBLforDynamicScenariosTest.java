package spectrum;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.but4reuse.adaptedmodel.AdaptedModel;
import org.but4reuse.adaptedmodel.Block;
import org.but4reuse.adaptedmodel.helpers.AdaptedModelHelper;
import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.jacoco.CoveredLineElement;
import org.but4reuse.adapters.jacoco.JacocoAdapter;
import org.but4reuse.adapters.javajdt.JavaJDTAdapter;
import org.but4reuse.adapters.javajdt.elements.CompilationUnitElement;
import org.but4reuse.adapters.javajdt.elements.FieldElement;
import org.but4reuse.adapters.javajdt.elements.MethodElement;
import org.but4reuse.adapters.javajdt.utils.JDTElementUtils;
import org.but4reuse.artefactmodel.Artefact;
import org.but4reuse.artefactmodel.ArtefactModel;
import org.but4reuse.artefactmodel.ArtefactModelFactory;
import org.but4reuse.benchmarks.argoumlspl.utils.TransformFLResultsToBenchFormat;
import org.but4reuse.block.identification.impl.SimilarElementsBlockIdentification;
import org.but4reuse.feature.location.LocatedFeature;
import org.but4reuse.feature.location.spectrum.RankingMetrics;
import org.but4reuse.feature.location.spectrum.SpectrumBasedLocalization;
import org.but4reuse.featurelist.Feature;
import org.but4reuse.featurelist.FeatureList;
import org.but4reuse.featurelist.FeatureListFactory;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import fk.stardust.localizer.IFaultLocalizer;
import metricsCalculation.MetricsCalculation;
import spectrum.utils.ConsoleProgressMonitor;
import spectrum.utils.HTMLReportUtils;
import spectrum.utils.TypeLevelMetricsCalculation;
import utils.FeatureUtils;
import utils.FileUtils;
import utils.ScenarioUtils;

public class Main_SBLforDynamicScenariosTest {


	static File benchmarkFolder = new File("C:\\ArgoUML-SPL\\ArgoUMLSPLBenchmark");
	//static File benchmarkFolder = new File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");

	static File originalVariantSrc = new File(benchmarkFolder,
			"scenarios/ScenarioOriginalVariant/variants/Original.config/src/org");

	static File jacocoExecutions = new File("execTraces/test");

	static File scenariosFolder = new File(benchmarkFolder, "scenarios");

	static List<File> scenarios = ScenarioUtils.getAllScenariosOrderedByNumberOfVariants(scenariosFolder);

	static float totallines = 0;
	static float previousTotalLines = 0;

	public static void main(String[] args) {

		// create artefact model and feature list
		FeatureList featureList = FeatureListFactory.eINSTANCE.createFeatureList();
		ArtefactModel artefactModel = ArtefactModelFactory.eINSTANCE.createArtefactModel();
		featureList.setArtefactModel(artefactModel);

		File testTraces = new File("execTraces/test");
		for (File trace : testTraces.listFiles()) {
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
		List<IAdapter> adapters = new ArrayList<IAdapter>();
		adapters.add(jacocoAdapter);
		AdaptedModel adaptedModel = AdaptedModelHelper.adapt(artefactModel, adapters, new ConsoleProgressMonitor());

		// Get blocks, one block per element
		SimilarElementsBlockIdentification blockIdentificationAlgo = new SimilarElementsBlockIdentification();
		List<Block> blocks = blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(), false,
				new ConsoleProgressMonitor());
		adaptedModel.getOwnedBlocks().addAll(blocks);

		// Launch feature location
		IFaultLocalizer<Block> wong2 = RankingMetrics.getRankingMetricByName("Wong2");
		SpectrumBasedLocalization featureLocationAlgo = new SpectrumBasedLocalization();
		List<LocatedFeature> flResult = featureLocationAlgo.locateFeatures(featureList, adaptedModel, wong2, 1.0,
				new ConsoleProgressMonitor());
		Map<String, Map<String, List<Integer>>> mapFeatureJavaLines = getResults(flResult);

		// adapt java source code of the original variant that was the one used for the
		// traces
		System.out.println("Adapting Original source code with JDT");
		IAdapter jdtAdapter = new JavaJDTAdapter();
		List<IElement> jdtElements = jdtAdapter.adapt(originalVariantSrc.toURI(), new ConsoleProgressMonitor());

		System.out.println("Transform feature results in Lines to IElements");
		Map<String, List<IElement>> mapFeatureIElements = transformLinesToIElements(mapFeatureJavaLines, jdtElements);

		// Apply rules to the JDT Elements
		Map<String, File> mapScenarioMetricsFile = new LinkedHashMap<String, File>();
		Map<String, File> mapScenarioMetricsFileTypeLevel = new HashMap<String, File>();

		// For each scenario
		for (File scenario : scenarios) {

			System.out.println("Current scenario: " + scenario.getName());

			// check if it was built
			if (!ScenarioUtils.isScenarioBuilt(scenario)) {
				System.out.println("Skip: The scenario variants were not derived.");
				continue;
			}

			FeatureUtils featureUtils = new FeatureUtils(scenario.getAbsolutePath(),
					new File(benchmarkFolder, "featuresInfo/features.txt").getAbsolutePath());

			Map<String, Set<String>> benchmarkResults = new LinkedHashMap<String, Set<String>>();

			System.out.println("Adapting all variants in the scenario");
			Map<String, List<IElement>> mapVariantIElements = new LinkedHashMap<String, List<IElement>>();
			for (String configId : featureUtils.getConfigurationIds()) {
				File variantSrc = new File(featureUtils.getVariantFolderOfConfig(configId) + "/src/org/argouml");
				URI variantSrcURI = variantSrc.toURI();
				List<IElement> elements = jdtAdapter.adapt(variantSrcURI, null);
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

				benchmarkResults.put(feature, benchmarkResultsCurrentFeature);
			}

			File outputFolder = new File("output_DynamicScenariosTest");
			File outputFolderScenario = new File(outputFolder, scenario.getName());
			File resultsFolder = new File(outputFolderScenario, "location");
			TransformFLResultsToBenchFormat.serializeResults(resultsFolder, benchmarkResults);

			// Metrics calculation
			System.out.println("Calculating metrics");
			String results = MetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"), resultsFolder);
			File resultsFile = new File(outputFolderScenario, "resultPrecisionRecall.csv");
			try {
				FileUtils.writeFile(resultsFile, results);
			} catch (Exception e) {
				e.printStackTrace();
			}

			// update html report
			System.out.println("Update html report");
			mapScenarioMetricsFile.put(scenario.getName(), resultsFile);
			List<String> featuresBeingConsidered = featureUtils.getFeatureIds();
			featuresBeingConsidered.remove("LOGGING");
			featuresBeingConsidered.remove("COGNITIVE");
			HTMLReportUtils.create(outputFolder, mapScenarioMetricsFile, featuresBeingConsidered);
			
			
			// Metrics calculation with type level ground truth 
			System.out.println("Calculating metrics with type level ground truth");
			String resultsTypeLevel = TypeLevelMetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"), resultsFolder);
			File resultsFileTypeLevel = new File(outputFolderScenario, "resultPrecisionRecallTypeLevel.csv");
			try {
				FileUtils.writeFile(resultsFileTypeLevel, resultsTypeLevel);
			} catch (Exception e) {
				e.printStackTrace();
			}

			// update html report type level ground truth
			System.out.println("Update html report type level ground truth");
			mapScenarioMetricsFileTypeLevel.put("Original", resultsFileTypeLevel);
			HTMLReportUtils.createReportNaiveResults(outputFolder, mapScenarioMetricsFileTypeLevel,featuresBeingConsidered, "reportTypeLevel");
		}
		// }
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
		totallines = visitor.totalLines;
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
