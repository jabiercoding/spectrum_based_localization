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
import org.but4reuse.adapters.javajdt.elements.MethodBodyElement;
import org.but4reuse.adapters.javajdt.elements.MethodElement;
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import fk.stardust.localizer.IFaultLocalizer;
import metricsCalculation.MetricsCalculation;
import spectrum.utils.ConsoleProgressMonitor;
import spectrum.utils.HTMLReportUtils;
import utils.FileUtils;
import utils.ScenarioUtils;

public class Main_SBLforDynamicScenariosManual {

	static File benchmarkFolder = new File("C:\\ArgoUML-SPL\\ArgoUMLSPLBenchmark");
	// static File benchmarkFolder = new
	// File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");

	static File originalVariantSrc = new File(benchmarkFolder,
			"scenarios/ScenarioOriginalVariant/variants/Original.config/src/org");

	static File jacocoExecutions = new File("execTraces/manual");

	static File scenariosFolder = new File(benchmarkFolder, "scenarios");

	static List<File> scenarios = ScenarioUtils.getAllScenariosOrderedByNumberOfVariants(scenariosFolder);

	static float totallines = 0;
	static float previousTotalLines = 0;

	public static void main(String[] args) {

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

		// Apply rules to the JDT Elements

		// adapt java source code
		System.out.println("Adapting source code with JDT");
		IAdapter jdtAdapter = new JavaJDTAdapter();
		List<CompilationUnitElement> compilationUnits = new ArrayList<CompilationUnitElement>();
		List<IElement> jdtElements = jdtAdapter.adapt(originalVariantSrc.toURI(), new ConsoleProgressMonitor());

		for (IElement element : jdtElements) {
			if (element instanceof CompilationUnitElement) {
				compilationUnits.add((CompilationUnitElement) element);
			}
			if (element instanceof MethodBodyElement) {
				// MethodBodyElement methodBody = (MethodBodyElement) element;
				// System.out.println("Method body of " +
				// methodBody.getDependencies().get("methodBody").get(0));
			} else if (element instanceof MethodElement) {
				// System.out.println(element);
			}
		}

		for (File scenario : scenarios) {
			if (!scenario.getName().equals("ScenarioOriginalVariant")) {
				System.out.println("Current scenario: " + scenario.getName());

				// check if it was built
				if (!ScenarioUtils.isScenarioBuilt(scenario)) {
					System.out.println("Skip: The scenario variants were not derived.");
					continue;
				}

				Map<String, Set<String>> benchmarkResults = new LinkedHashMap<String, Set<String>>();

				// for each feature transform from executed lines to JDT
				// elements
				for (String feature : mapFeatureJavaLines.keySet()) {
					String xmlfile = feature + ".xml";
					File featureExecutions = new File(jacocoExecutions, xmlfile);
					Multimap<String, Integer> elementsJacoco = ArrayListMultimap.create();
					Main.adapt(featureExecutions, elementsJacoco);
					ArrayList<IElement> jdtElementsJacoco = new ArrayList<>();

					Set<String> benchmarkResultsCurrentFeature = new LinkedHashSet<String>();

					System.out.println("Feature: " + feature);

					for (String javaFile : elementsJacoco.keySet()) {
						CompilationUnitElement compUnit = getCompilationUnitElement(compilationUnits, javaFile);
						if (compUnit == null) {
							System.out.println("Not found: " + javaFile);
							continue;
						}
						CompilationUnit cu = (CompilationUnit) compUnit.node;

						// to remove repetitive jdt elements
						for (Integer line : elementsJacoco.get(javaFile)) {
							// System.out.println(line);
							IElement element = getJDTElement(cu, line, javaFile);
							if (element != null && !jdtElementsJacoco.contains(element))
								jdtElementsJacoco.add(element);
						}
					}

					ArrayList<String> featuresVariant = new ArrayList<>();
					File configsFolder = Main.getFileofFileByName(scenario, "configs", 0);

					// Get all config files
					File[] configFile = configsFolder.listFiles();
					ArrayList<IElement> elementsToRemove = new ArrayList<>();

					// first rule
					// remove all jdt elements in common with variants that do
					// not
					// contain the feature
					for (int i = 0; i < configFile.length; i++) {
						// get features of a variant

						List<String> lines = FileUtils.getLinesOfFile(configFile[i]);
						if (lines.size() > 0) {
							for (int n = 0; n < lines.size(); n++)
								featuresVariant.add(lines.get(n));
						}

						// if the variant does not contain the feature of the
						// execution traces
						// search for common jdt elements to remove from
						// jdtElementsJacoco
						if (!featuresVariant.contains(feature)) {
							System.out.println("VARIANT: " + configFile[i].getAbsolutePath().toString());
							String variantpath = configFile[i].getAbsolutePath().replace("configs", "variants");
							File variant = new File(variantpath + "/src/org/argouml");
							URI uri = variant.toURI();

							List<IElement> elements = jdtAdapter.adapt(uri, null);
							for (IElement iElement : jdtElementsJacoco) {
								if (elements.contains(iElement) && !elementsToRemove.contains(iElement)) {
									elementsToRemove.add(iElement);
								}
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
					for (int i = 0; i < configFile.length; i++) {
						if (featuresVariant.contains(feature)) {
							File variantPath = new File(configFile[i].getAbsolutePath().replace("configs", "variants"),
									"src/org");
							List<IElement> jdtElementsVariant = jdtAdapter.adapt(variantPath.toURI(),
									new ConsoleProgressMonitor());
							elementsVariants.put(configFile[i].getName(), jdtElementsVariant);
						}
					}

					// for each List of jdt elements get the ones that are in
					// common
					// with all variants that contain the feature
					for (Map.Entry<String, List<IElement>> elementsVariant : elementsVariants.entrySet()) {
						for (IElement iElement : elementsVariant.getValue()) {
							Boolean commonAllVariants = false;
							for (Map.Entry<String, List<IElement>> elementsVariantAux : elementsVariants.entrySet()) {
								if (!elementsVariantAux.getKey().equals(elementsVariant.getKey())) {
									if (elementsVariantAux.getValue().contains(iElement)) {
										commonAllVariants = true;
									} else {
										commonAllVariants = false;
										break;
									}
								}
							}
							if (commonAllVariants && !elementsInCommonAllVariantsWithFeature.contains(iElement))
								elementsInCommonAllVariantsWithFeature.add(iElement);
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
						if (element instanceof CompilationUnitElement) {
							TypeDeclaration type = (TypeDeclaration) element;
							benchmarkResultsCurrentFeature
									.add(org.but4reuse.benchmarks.argoumlspl.utils.TraceIdUtils.getId(type));
							compilationUnitsAfterTwoRules.add((CompilationUnitElement) element);
						}
						if (element instanceof MethodBodyElement) {
							// MethodBodyElement methodBody =
							// (MethodBodyElement)
							// element;
							// System.out.println("Method body of " +
							// methodBody.getDependencies().get("methodBody").get(0));
						} else if (element instanceof MethodElement) {
							// System.out.println(element);
						}
					}

					benchmarkResults.put(feature, benchmarkResultsCurrentFeature);
				}

				File outputFolder = new File("output_DyamicScenariosManual");
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
				Map<String, File> mapScenarioMetricsFile = new HashMap<String, File>();
				mapScenarioMetricsFile.put(scenario.getName(), resultsFile);
				HTMLReportUtils.create(outputFolderScenario, mapScenarioMetricsFile);
			}
		}
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
