package spectrum;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.but4reuse.adaptedmodel.AdaptedArtefact;
import org.but4reuse.adaptedmodel.AdaptedModel;
import org.but4reuse.adaptedmodel.AdaptedModelFactory;
import org.but4reuse.adaptedmodel.Block;
import org.but4reuse.adaptedmodel.helpers.AdaptedModelHelper;
import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.jacoco.CoveredLineElement;
import org.but4reuse.adapters.jacoco.JacocoAdapter;
import org.but4reuse.adapters.javajdt.JavaJDTAdapter;
import org.but4reuse.artefactmodel.Artefact;
import org.but4reuse.artefactmodel.ArtefactModel;
import org.but4reuse.benchmarks.argoumlspl.utils.GenerateScenarioResources;
import org.but4reuse.block.identification.IBlockIdentification;
import org.but4reuse.fca.block.identification.FCABlockIdentification;
import org.but4reuse.featurelist.FeatureList;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import utils.FileUtils;
import utils.JDTUtils;

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

			// List<IElement> elements = jdtAdapter.adapt(uri, null);

			List<IElement> elementsJacoco = JacocoAdapter.adapt(jacocoExecutions);
			Multimap<CompilationUnit, IElement> compilationUnitsExecuted = ArrayListMultimap.create();
			// Map<CompilationUnit, ArrayList<IElement>>
			// compilationUnitsExecuted = new HashMap<>();

			for (IElement e : elementsJacoco) {

				CoveredLineElement elementJacoco = (CoveredLineElement) e;

				// Prepare the parser
				ASTParser parser = ASTParser.newParser(AST.JLS8);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				parser.setBindingsRecovery(true);

				String pathOriginalVariant = "";
				if (elementJacoco.getPackageName().contains("argouml")) {
					pathOriginalVariant = uri.getPath().substring(1)
							+ elementJacoco.getPackageName()
									.substring(elementJacoco.getPackageName().indexOf("argouml") + 7)
							+ File.separator + elementJacoco.getFileName();
				} else {
					pathOriginalVariant = uri.getPath().substring(1, uri.getPath().indexOf("org") + 3) + File.separator
							+ elementJacoco.getPackageName().substring(elementJacoco.getPackageName().indexOf("omg"))
							+ File.separator + elementJacoco.getFileName();
				}
				String source = FileUtils.getStringOfFile(new File(pathOriginalVariant));
				parser.setSource(source.toCharArray());
				CompilationUnit cu = (CompilationUnit) parser.createAST(null);

				List<MethodDeclaration> methods = getMethods(cu);
				List<MethodDeclaration> methodsCovered = new ArrayList<MethodDeclaration>();
				List<FieldDeclaration> fields = getFields(cu);
				List<FieldDeclaration> fieldsCovered = new ArrayList<FieldDeclaration>();

				// get the method of this line
				int position = cu.getPosition(elementJacoco.getLineNumber(), 0);
				MethodDeclaration method = JDTUtils.getMethodThatContainsAPosition(methods, position, position);
				if (method != null) { // methodDeclaration
					IElement element = getJDTElement(cu, elementJacoco.getLineNumber(), elementJacoco.getFileName());
					if (element != null) {
						compilationUnitsExecuted.put(cu, element);
					}

				} else {// FieldDeclaration
					FieldDeclaration field = JDTUtils.getFieldThatContainsAPosition(fields, position, position);
					if (field != null) {
						IElement element = getJDTElement(cu, elementJacoco.getLineNumber(),
								elementJacoco.getFileName());
						if (element != null) {
							compilationUnitsExecuted.put(cu, element);
						}
					}
				}
			}
			

			/*for (CompilationUnit key : compilationUnitsExecuted.keySet()) {
				ArrayList<IElement> elements = (ArrayList<IElement>) compilationUnitsExecuted.get(key);
	            for (int i = 0; i < elements.size(); i++) {
	                for (int j = i + 1; j < elements.size(); j++) {
	                    System.out.println(elements.get(i) + "," + elements.get(j));
	                }
	            }
	            System.out.println();
	        }
			*/
			
		}

		// Get blocks
		IBlockIdentification blockIdentificationAlgo = new FCABlockIdentification();
		List<Block> blocks = blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(),
				new NullProgressMonitor());
		adaptedModel.getOwnedBlocks().addAll(blocks);

		// Launch feature location SpectrumBasedLocalization
		// featureLocationAlgo = new SpectrumBasedLocalization();
		// List<LocatedFeature> flResult =
		// featureLocationAlgo.locateFeatures(fl,
		// adaptedModel, new NullProgressMonitor());

	}

	private static IElement getJDTElement(final CompilationUnit cu, Integer lineNumber, String fileName) {

		// Visit the cu to find the element corresponding to a line
		// cu.accept();
		ASTVisitorGabriela visitor = new ASTVisitorGabriela(cu, lineNumber, fileName);
		cu.accept(visitor);
		return visitor.e;
	}

	/**
	 * Get fields ignoring those in anonymous classes
	 * 
	 * @param cu
	 * @return
	 */
	public static List<FieldDeclaration> getFields(CompilationUnit cu) {
		List<FieldDeclaration> fields = JDTUtils.getFields(cu);
		List<FieldDeclaration> toRemove = new ArrayList<FieldDeclaration>();
		for (FieldDeclaration field : fields) {
			if (field.getParent() instanceof AnonymousClassDeclaration) {
				toRemove.add(field);
			}
		}
		fields.removeAll(toRemove);
		return fields;
	}

	/**
	 * Get methods ignoring those in anonymous classes
	 * 
	 * @param cu
	 * @return
	 */
	public static List<MethodDeclaration> getMethods(CompilationUnit cu) {
		List<MethodDeclaration> methods = JDTUtils.getMethods(cu);
		List<MethodDeclaration> toRemove = new ArrayList<MethodDeclaration>();
		for (MethodDeclaration method : methods) {
			if (method.getParent() instanceof AnonymousClassDeclaration) {
				toRemove.add(method);
			}
		}
		methods.removeAll(toRemove);
		return methods;
	}

	/**
	 * Get only java classes from the feature variant directory
	 * 
	 * @param featureVariantDir
	 * @param files
	 * @return
	 */
	public static void getFilesToProcess(File featureVariantDir, List<File> files) {
		if (featureVariantDir.isDirectory()) {
			for (File file : featureVariantDir.listFiles()) {
				if (!files.contains(featureVariantDir) && !file.getName().equals(featureVariantDir.getName()))
					files.add(featureVariantDir);
				getFilesToProcess(file, files);
			}
		} else if (featureVariantDir.isFile() && featureVariantDir.getName()
				.substring(featureVariantDir.getName().lastIndexOf('.') + 1).equals("java")) {
			files.add(featureVariantDir);
		}
	}

}
