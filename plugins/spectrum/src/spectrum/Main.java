package spectrum;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.but4reuse.adaptedmodel.AdaptedModel;
import org.but4reuse.adaptedmodel.Block;
import org.but4reuse.adaptedmodel.helpers.AdaptedModelHelper;
import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.javajdt.JavaJDTAdapter;
import org.but4reuse.artefactmodel.ArtefactModel;
import org.but4reuse.benchmarks.argoumlspl.utils.GenerateScenarioResources;
import org.but4reuse.block.identification.IBlockIdentification;
import org.but4reuse.fca.block.identification.FCABlockIdentification;
import org.but4reuse.feature.location.IFeatureLocation;
import org.but4reuse.feature.location.LocatedFeature;
import org.but4reuse.feature.location.spectrum.SpectrumBasedLocalization;
import org.but4reuse.featurelist.FeatureList;
import org.eclipse.core.runtime.NullProgressMonitor;

public class Main {
	public static void main(String[] args) {
		//
		File scenariosFolder = new File("C:/absPath");

		File originalScenario = new File(scenariosFolder, "ScenarioOriginalVariant");

		// Get the artefact model and feature list of the scenario
		Object[] amAndFl = GenerateScenarioResources.createArtefactModelAndFeatureList(originalScenario);
		ArtefactModel am = (ArtefactModel) amAndFl[0];
		FeatureList fl = (FeatureList) amAndFl[1];

		// Adapt the variants
		IAdapter jdtAdapter = new JavaJDTAdapter();
		// List<IElement> elements = jdtAdapter.adapt(uri, null);
		// IElement element = getElementInLine(elements, String javaFile, Integer line);
		
		List<IAdapter> adapters = new ArrayList<IAdapter>();
		adapters.add(jdtAdapter);
		AdaptedModel adaptedModel = AdaptedModelHelper.adapt(am, adapters, null);

		// Get blocks
		IBlockIdentification blockIdentificationAlgo = new FCABlockIdentification();
		List<Block> blocks = blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(), null);
		adaptedModel.getOwnedBlocks().addAll(blocks);

		// Launch feature location
		SpectrumBasedLocalization featureLocationAlgo = new SpectrumBasedLocalization();
		List<LocatedFeature> flResult = featureLocationAlgo.locateFeatures(fl, adaptedModel, new NullProgressMonitor());

		
	}
}
