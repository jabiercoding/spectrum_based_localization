package spectrum;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
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
import org.but4reuse.adapters.javajdt.elements.TypeElement;
import org.but4reuse.adapters.javajdt.utils.JDTElementUtils;
import org.but4reuse.artefactmodel.Artefact;
import org.but4reuse.artefactmodel.ArtefactModel;
import org.but4reuse.artefactmodel.ArtefactModelFactory;
import org.but4reuse.benchmarks.argoumlspl.utils.TraceIdUtils;
import org.but4reuse.benchmarks.argoumlspl.utils.TransformFLResultsToBenchFormat;
import org.but4reuse.block.identification.impl.SimilarElementsBlockIdentification;
import org.but4reuse.feature.location.LocatedFeature;
import org.but4reuse.feature.location.spectrum.RankingMetrics;
import org.but4reuse.feature.location.spectrum.SpectrumBasedLocalization;
import org.but4reuse.featurelist.Feature;
import org.but4reuse.featurelist.FeatureList;
import org.but4reuse.featurelist.FeatureListFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import fk.stardust.localizer.IFaultLocalizer;
import metricsCalculation.MetricsCalculation;
import spectrum.utils.ConsoleProgressMonitor;
import spectrum.utils.HTMLReportUtils;
import utils.FileUtils;

public class Main_SBLforDynamicOriginalManual {

	static File benchmarkFolder = new File("C:\\ArgoUML-SPL\\ArgoUMLSPLBenchmark");
	// static File benchmarkFolder = new
	// File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");

	static File originalVariantSrc = new File(benchmarkFolder,
			"scenarios/ScenarioOriginalVariant/variants/Original.config/src/org");

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
				MethodBodyElement methodBody = (MethodBodyElement) element;
				// System.out.println("Method body of " +
				// methodBody.getDependencies().get("methodBody").get(0));
			} else if (element instanceof MethodElement) {
				// System.out.println(element);
			}
		}

		Map<String, Set<String>> benchmarkResults = new LinkedHashMap<String, Set<String>>();
		// transform from lines to JDT elements and print the percentage covered
		// for
		// each method
		for (String feature : mapFeatureJavaLines.keySet()) {
			Set<String> benchmarkResultsCurrentFeature = new LinkedHashSet<String>();
			Map<String, List<Integer>> javaFiles = mapFeatureJavaLines.get(feature);
			Map<IElement, Float> linesCoveredMethod = new HashMap<>();
			System.out.println("Feature: " + feature);
			for (String javaFile : javaFiles.keySet()) {
				CompilationUnitElement compUnit = getCompilationUnitElement(compilationUnits, javaFile);
				if (compUnit == null) {
					System.out.println("Not found: " + javaFile);
					continue;
				}
				for (TypeElement typeElement : JDTElementUtils.getTypes(compUnit)) {
					// naive solution: adding a class-level localization when
					// there is at least one line in the class.
					TypeDeclaration type = (TypeDeclaration) typeElement.node;
					benchmarkResultsCurrentFeature
							.add(org.but4reuse.benchmarks.argoumlspl.utils.TraceIdUtils.getId(type));
				}

				CompilationUnit cu = (CompilationUnit) compUnit.node;
				
				//get percentage of coverage for each method executed
				List<Integer> lines = javaFiles.get(javaFile);
				Collections.sort(lines);
				float count = 0;
				IElement previous = null;
				IElement element = null;
				for (Integer line : lines) {
					element = getJDTElement(cu, line, javaFile);
					if (element != null) {
						if (element instanceof MethodElement) {
							if (previous == null) {
								previous = element;
								previousTotalLines = totallines;
								count++;
							} else if (previous.equals(element)) {
								count++;
							} else {
								Float percentageCovered = (count * 100) / previousTotalLines;
								linesCoveredMethod.put(previous, percentageCovered);
								System.out.println(percentageCovered + "%" + " " + previous);
								count = 1;
								previous = element;
								previousTotalLines = totallines;
							}
						}

					} else {
						previous = null;
					}
				}
				if (previous != null && element != null && previous.equals(element)) {
					Float percentageCovered = (count * 100) / totallines;
					linesCoveredMethod.put(previous, percentageCovered);
					System.out.println(percentageCovered + "%" + " " + previous);
				}
			}
			
			benchmarkResults.put(feature, benchmarkResultsCurrentFeature);
		}

		File outputFolder = new File("output_DyamicOriginalManual");
		File resultsFolder = new File(outputFolder, "location");
		TransformFLResultsToBenchFormat.serializeResults(resultsFolder, benchmarkResults);

		// Metrics calculation
		System.out.println("Calculating metrics");
		String results = MetricsCalculation.getResults(new File(benchmarkFolder, "groundTruth"), resultsFolder);
		File resultsFile = new File(outputFolder, "resultPrecisionRecall.csv");
		try {
			FileUtils.writeFile(resultsFile, results);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// update html report
		System.out.println("Update html report");
		Map<String, File> mapScenarioMetricsFile = new HashMap<String, File>();
		mapScenarioMetricsFile.put("Original", resultsFile);
		HTMLReportUtils.create(outputFolder, mapScenarioMetricsFile);
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
