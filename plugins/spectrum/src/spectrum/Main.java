package spectrum;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.but4reuse.adaptedmodel.AdaptedArtefact;
import org.but4reuse.adaptedmodel.AdaptedModel;
import org.but4reuse.adaptedmodel.AdaptedModelFactory;
import org.but4reuse.adaptedmodel.helpers.AdaptedModelHelper;
import org.but4reuse.adapters.IAdapter;
import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.javajdt.JavaJDTAdapter;
import org.but4reuse.artefactmodel.Artefact;
import org.but4reuse.artefactmodel.ArtefactModel;
import org.but4reuse.benchmarks.argoumlspl.utils.GenerateScenarioResources;
import org.but4reuse.featurelist.FeatureList;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import utils.FileUtils;
import utils.JDTUtils;
import utils.ScenarioUtils;

public class Main {

	static File benchmarkFolder = new File("/Users/brunomachado/eclipse-workspace/ArgoUMLSPLBenchmark");
	// File("C:\\git\\argouml-spl-benchmark\\ArgoUMLSPLBenchmark");

	static File jacocoExecutions = new File(
			"C:\\Users\\gabil\\Desktop\\PHD\\DynamicFL\\JSS paper\\Execution_GUI_Diagram_Features\\ACTIVITYDIAGRAM\\traces.xml");

	static File scenariosFolder = new File(benchmarkFolder, "scenarios");

	static File originalScenario = new File(scenariosFolder, "ScenarioOriginalVariant");

	static File originalVariant = new File(benchmarkFolder,
			"scenarios/ScenarioOriginalVariant/variants/Original.config/src");

	static File variantsFolder = new File(scenariosFolder, "ScenarioRandom003Variants");
	

	public static void main(String[] args) {

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

		ArrayList<IElement> jdtElementsJacoco = new ArrayList<>();
		String feature = jacocoExecutions.getAbsolutePath().substring(
				jacocoExecutions.getAbsolutePath().indexOf("Features") + 9,
				jacocoExecutions.getAbsolutePath().indexOf("traces") - 1);

		for (Artefact a : am.getOwnedArtefacts()) {
			URI uri = null;
			try {
				uri = new URI(a.getArtefactURI());
			} catch (URISyntaxException e) {
				e.printStackTrace();
				System.out.println("Exception get URI: " + e);
			}

			Multimap<String, Integer> elementsJacoco = ArrayListMultimap.create();
			adapt(jacocoExecutions, elementsJacoco);

			ArrayList<String> nullelements = new ArrayList<>();
			for (String javaFile : elementsJacoco.keySet()) {
				// System.out.println("Class: " + javaFile);
				CompilationUnit cu = getCompilationUnit(javaFile);
				for (Integer line : elementsJacoco.get(javaFile)) {
					// System.out.println(line);
					IElement element = getJDTElement(cu, line, javaFile);
					if (element != null) {
						if (!jdtElementsJacoco.contains(element))
							jdtElementsJacoco.add(element);
					} else {
						nullelements.add("Element null in line: " + line + " class: " + javaFile);
					}
				}
			}
		}

		File configsFolder = getFileofFileByName(variantsFolder, "configs", 0);

		// Get all config files
		File[] configFile = configsFolder.listFiles();
		ArrayList<IElement> elementsToRemove = new ArrayList<>();
		for (int i = 0; i < configFile.length; i++) {
			// get features
			ArrayList<String> featuresVariant = new ArrayList<>();
			List<String> lines = FileUtils.getLinesOfFile(configFile[i]);
			if (lines.size() > 0) {
				for (int n = 0; n < lines.size(); n++)
					featuresVariant.add(lines.get(n));
			}

			//search for common IElements to remove from jdtElementsJacoco when the set of features of the variant does not contain the feature of the execution traces
			if (!featuresVariant.contains(feature)) {
				System.out.println("VARIANT: "+configFile[i].getAbsolutePath().toString());
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
		System.out.println("SIZE elementsToRemove: "+elementsToRemove.size());
		System.out.println("SIZE jdtElementsJacoco before remove: "+jdtElementsJacoco.size());
		jdtElementsJacoco.removeAll(elementsToRemove);
		System.out.println("SIZE jdtElementsJacoco after remove: "+jdtElementsJacoco.size());
		
		
		// Get blocks
		// IBlockIdentification blockIdentificationAlgo = new
		// FCABlockIdentification();
		// List<Block> blocks =
		// blockIdentificationAlgo.identifyBlocks(adaptedModel.getOwnedAdaptedArtefacts(),
		// new NullProgressMonitor());
		// adaptedModel.getOwnedBlocks().addAll(blocks);

		// Launch feature location SpectrumBasedLocalization
		// featureLocationAlgo = new SpectrumBasedLocalization();
		// List<LocatedFeature> flResult =
		// featureLocationAlgo.locateFeatures(fl,
		// adaptedModel, new NullProgressMonitor());

	}

	/**
	 * Search and retrieve one file with an specific name and also it can
	 * specific the type of file.
	 * 
	 * @param parentFile
	 *            - Location of the file
	 * @param nameOfFile
	 *            - name of the file of interest
	 * @param typeOfFile
	 *            - 0 Folder, 1 File, other does not matter
	 * @return
	 */
	public static File getFileofFileByName(File parentFile, String nameOfFile, int typeOfFile) {

		File fileOfInterest = null;
		try {

			// if typeOfFile is 0 search for folderFiles
			if (typeOfFile == 0) {
				// Get the two folders of interest inside each Scenario Folder
				for (File scenarioSubFolder : parentFile.listFiles()) {
					// We are just interested in the folder which is called
					// configs
					if (scenarioSubFolder.isDirectory() && scenarioSubFolder.getName().equals(nameOfFile))
						fileOfInterest = scenarioSubFolder;
				}
				// if typeOfFile is 1 search for file
			} else if (typeOfFile == 1) {
				// Get the two folders of interest inside each Scenario Folder
				for (File scenarioSubFolder : parentFile.listFiles()) {
					// We are just interested in the folder which is called
					// configs
					if (scenarioSubFolder.isFile() && scenarioSubFolder.getName().equals(nameOfFile))
						fileOfInterest = scenarioSubFolder;
				}
				// otherwise search any kind of file
			} else {
				// Get the two folders of interest inside each Scenario Folder
				for (File scenarioSubFolder : parentFile.listFiles()) {
					// We are just interested in the folder which is called
					// configs
					if (scenarioSubFolder.getName().equals(nameOfFile))
						fileOfInterest = scenarioSubFolder;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return fileOfInterest;

	}

	private static CompilationUnit getCompilationUnit(String fileName) {
		// Prepare the parser
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setBindingsRecovery(true);
		String source = FileUtils.getStringOfFile(new File(originalVariant, fileName));
		parser.setSource(source.toCharArray());
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		return cu;
	}

	public static void adapt(File xmlFile, Multimap<String, Integer> elementsJacoco) {

		// parse the XML file using StaX
		try {
			XMLInputFactory inputFactory = XMLInputFactory.newInstance();
			// Setup a new eventReader
			InputStream in = new FileInputStream(xmlFile);
			XMLEventReader eventReader = inputFactory.createXMLEventReader(in);

			String currentPackage = null;
			String currentSourceFile = null;

			while (eventReader.hasNext()) {
				XMLEvent event = eventReader.nextEvent();

				if (event.isStartElement()) {
					StartElement startElement = event.asStartElement();
					String elementName = startElement.getName().getLocalPart();

					if (elementName.equals("package")) {
						currentPackage = startElement.getAttributeByName(new QName("name")).getValue();
					}

					if (elementName.equals("sourcefile")) {
						currentSourceFile = startElement.getAttributeByName(new QName("name")).getValue();
					}

					if (elementName.equals("line")) {
						String lineNumber = startElement.getAttributeByName(new QName("nr")).getValue();
						String coveredInstructions = startElement.getAttributeByName(new QName("ci")).getValue();
						Integer execInt = Integer.parseInt(coveredInstructions);
						if (execInt > 0) {
							// add line
							elementsJacoco.put(currentPackage + "/" + currentSourceFile, Integer.parseInt(lineNumber));
						}
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static IElement getJDTElement(final CompilationUnit cu, Integer lineNumber, String fileName) {

		// Visit the cu to find the element corresponding to a line
		TransformLinesToJDTElements visitor = new TransformLinesToJDTElements(cu, lineNumber, fileName);
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
