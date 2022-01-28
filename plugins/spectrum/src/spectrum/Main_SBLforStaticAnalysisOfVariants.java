package spectrum;

import java.io.File;
import java.util.ArrayList;
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
import org.but4reuse.feature.location.spectrum.RankingMetrics;
import org.but4reuse.feature.location.spectrum.SpectrumBasedLocalization;
import org.but4reuse.featurelist.Feature;
import org.but4reuse.featurelist.FeatureList;
import org.but4reuse.featurelist.helpers.FeatureListHelper;

import fk.stardust.localizer.IFaultLocalizer;
import spectrum.utils.ConsoleProgressMonitor;

public class Main_SBLforStaticAnalysisOfVariants {
	
	public static void main(String[] args) {

		File benchmarkFolder = new File("C:/git/argouml-spl-benchmark/ArgoUMLSPLBenchmark");

		File scenariosFolder = new File(benchmarkFolder, "scenarios");

		File originalScenario = new File(scenariosFolder, "ScenarioTraditionalVariants");

		// Get the artefact model and feature list of the scenario
		Object[] amAndFl = GenerateScenarioResources.createArtefactModelAndFeatureList(originalScenario, false);
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
		// Using similar elements we will get one block for each element
		IBlockIdentification blockIdentificationAlgo = new FCABlockIdentification();
		List<Block> blocks = blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(),
				new ConsoleProgressMonitor());
		adaptedModel.getOwnedBlocks().addAll(blocks);

//		// Launch feature location
//		IFaultLocalizer<Block> wong2 = RankingMetrics.getRankingMetricByName("Wong2");
//		SpectrumBasedLocalization featureLocationAlgo = new SpectrumBasedLocalization();
//		List<LocatedFeature> flResult = featureLocationAlgo.locateFeatures(fl, adaptedModel, wong2, new ConsoleProgressMonitor());

		IFeatureLocation featureLocationAlgo = new StrictFeatureSpecificFeatureLocation();
		List<LocatedFeature> flResult = featureLocationAlgo.locateFeatures(fl, adaptedModel, new ConsoleProgressMonitor());
		
		Map<String, Set<String>> benchmarkResults = TransformFLResultsToBenchFormat.transform(fl, adaptedModel, flResult);
		
		TransformFLResultsToBenchFormat.serializeResults(new File("output"), benchmarkResults);
	}
}
