package spectrum;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.PackageElement;

import org.but4reuse.adaptedmodel.AdaptedArtefact;
import org.but4reuse.adaptedmodel.AdaptedModel;
import org.but4reuse.adaptedmodel.AdaptedModelFactory;
import org.but4reuse.adaptedmodel.Block;
import org.but4reuse.adaptedmodel.helpers.AdaptedModelHelper;
import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.eclipse.PluginElement;
import org.but4reuse.adapters.jacoco.CoveredLineElement;
import org.but4reuse.adapters.jacoco.JacocoAdapter;
import org.but4reuse.adapters.javajdt.JavaJDTAdapter;
import org.but4reuse.artefactmodel.Artefact;
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

		File benchmarkFolder = new File("C:\\ArgoUML-SPL\\ArgoUMLSPLBenchmark");
		// File("C:\\git\\argouml-spl-benchmark\\ArgoUMLSPLBenchmark");

		File jacocoExecutions = new File(
				"C:\\Users\\gabil\\Desktop\\PHD\\DynamicFL\\JSS paper\\Execution_GUI_Diagram_Features\\ACTIVITYDIAGRAM\\traces.xml");

		File scenariosFolder = new File(benchmarkFolder, "scenarios");

		File originalScenario = new File(scenariosFolder, "ScenarioOriginalVariant");

		// Get the artefact model and feature list of the scenario
		Object[] amAndFl = GenerateScenarioResources.createArtefactModelAndFeatureList(originalScenario, false);
		ArtefactModel am = (ArtefactModel) amAndFl[0];
		FeatureList fl = (FeatureList) amAndFl[1];

		// Adapt the variants and create the adapted model
		IAdapter jdtAdapter = new JavaJDTAdapter();
		List<IAdapter> adapters = new ArrayList<IAdapter>();
		adapters.add(jdtAdapter);
		AdaptedModel adaptedModel = AdaptedModelFactory.eINSTANCE.createAdaptedModel();
		for (Artefact a : am.getOwnedArtefacts()) {
			AdaptedArtefact adaptedArtefact = AdaptedModelHelper.adapt(a, adapters, new NullProgressMonitor());
			adaptedModel.getOwnedAdaptedArtefacts().add(adaptedArtefact);
		}

		for (Artefact a : am.getOwnedArtefacts()) {
			URI uri = null;
			try {
				uri = new URI(a.getArtefactURI());
			} catch (URISyntaxException e) {
				e.printStackTrace();
				System.out.println("Exception get URI: " + e);
			}

			List<IElement> elements = jdtAdapter.adapt(uri, null);

			Map<String, IElement> elementsJava = new HashMap<>();

			for (IElement e : elements) {
				elementsJava.put(e.getText(), e);
			}

			List<IElement> elementsJacoco = JacocoAdapter.adapt(jacocoExecutions);

			for (IElement e : elementsJacoco) {
				IElement elementJava = elementsJava.get(e.getText());
				IElement element = getElementInLine(((CoveredLineElement) e), elementJava);

			}

		}

		// Get blocks
		IBlockIdentification blockIdentificationAlgo = new FCABlockIdentification();
		List<Block> blocks = blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(),
				new NullProgressMonitor());
		adaptedModel.getOwnedBlocks().addAll(blocks);

		// Launch feature location SpectrumBasedLocalization
		//featureLocationAlgo = new SpectrumBasedLocalization();
		//List<LocatedFeature> flResult = featureLocationAlgo.locateFeatures(fl, adaptedModel, new NullProgressMonitor());

	}

	public static IElement getElementInLine(CoveredLineElement e, IElement elementJava) {

		IElement element = null;

		return element;

	}
}
