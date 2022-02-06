package spectrum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.but4reuse.adapters.IDependencyObject;
import org.but4reuse.adapters.IElement;
import org.but4reuse.adapters.impl.AbstractElement;
import org.but4reuse.adapters.javajdt.IdUtils;
import org.but4reuse.adapters.javajdt.elements.CompilationUnitElement;
import org.but4reuse.adapters.javajdt.elements.FieldElement;
import org.but4reuse.adapters.javajdt.elements.ImportElement;
import org.but4reuse.adapters.javajdt.elements.JDTElement;
import org.but4reuse.adapters.javajdt.elements.MethodBodyElement;
import org.but4reuse.adapters.javajdt.elements.MethodElement;
import org.but4reuse.adapters.javajdt.elements.PackageElement;
import org.but4reuse.adapters.javajdt.elements.TypeElement;
import org.but4reuse.adapters.javajdt.elements.TypeExtendsElement;
import org.but4reuse.adapters.javajdt.elements.TypeImplementsElement;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Visit the AST nodes to retrieve the JDT element corresponding to a line
 * number executed
 * 
 */
public class TransformLinesToJDTElements extends ASTVisitor {

	public static final String DEPENDENCY_IMPLEMENTS = "implements";
	public static final String DEPENDENCY_IMPLEMENTED_TYPE = "implementedType";
	public static final String DEPENDENCY_EXTENDS = "extends";
	public static final String DEPENDENCY_EXTENDED_TYPE = "extendedType";
	public static final String DEPENDENCY_IMPORTED_TYPE = "importedType";
	public static final String DEPENDENCY_METHOD_BODY = "methodBody";
	public static final String DEPENDENCY_PACKAGE = "package";
	public static final String DEPENDENCY_TYPE = "type";
	public static final String DEPENDENCY_COMPILATION_UNIT = "compilationUnit";

	static Map<String, JDTElement> packagesMap;
	static Map<String, JDTElement> typesMap;
	static Map<String, JDTElement> methodsMap;

	public JDTElement currentTypeElement;
	public JDTElement currentPackageElement;
	public List<IElement> elements;
	public IElement e;
	public JDTElement currentCompilationUnitElement;
	public CompilationUnit cu;
	public Integer lineNumber;
	public Integer totalLines;

	public List<IElement> newElements = new ArrayList<IElement>();

	public TransformLinesToJDTElements(CompilationUnit cu, Integer lineNumber, String fileName) {
		this.cu = cu;
		this.lineNumber = lineNumber;

		this.currentTypeElement = null;
		this.currentPackageElement = null;

		packagesMap = new HashMap<String, JDTElement>();
		typesMap = new HashMap<String, JDTElement>();
		methodsMap = new HashMap<String, JDTElement>();
		elements = new ArrayList<IElement>();
		// IElement to return for the corresponding lineNumber
		this.e = null;

		this.currentCompilationUnitElement = new CompilationUnitElement();
		this.currentCompilationUnitElement.node = cu;
		this.currentCompilationUnitElement.name = fileName;
		this.currentCompilationUnitElement.id = IdUtils.getId(cu.getPackage()) + " " + fileName;
		elements.add(currentCompilationUnitElement);
	}

	// Packages
	public boolean visit(PackageDeclaration node) {
		String id = IdUtils.getId(node);
		// Check if we already have it, otherwise create it
		JDTElement element = packagesMap.get(id);
		if (element == null) {
			element = new PackageElement();
			element.node = node;
			element.name = node.getName().getFullyQualifiedName();
			element.id = id;
			packagesMap.put(id, element);
			elements.add(element);
		}
		currentPackageElement = element;
		return true;
	}

	// Types (Classes, Interfaces etc.)
	public boolean visit(TypeDeclaration node) {
		JDTElement element = new TypeElement();
		element.node = node;
		element.name = node.getName().getIdentifier();
		StringBuffer qname = new StringBuffer();
		if (currentPackageElement != null) {
			qname.append(currentPackageElement.id);
			qname.append(".");
		}
		qname.append(element.name);
		element.id = qname.toString();
		elements.add(element);
		currentTypeElement = element;

		typesMap.put(element.id, element);

		// Add dependency to the Package
		element.addDependency(DEPENDENCY_PACKAGE, currentPackageElement);

		// Add dependency to compilation unit
		element.addDependency(DEPENDENCY_COMPILATION_UNIT, currentCompilationUnitElement);
		int start = cu.getLineNumber(node.getStartPosition());
		int end = cu.getLineNumber(node.getStartPosition() + node.getLength());
		if (lineNumber >= start && lineNumber <= end) {
			this.e = element;
		}
		return true;
	}

	// Methods
	public boolean visit(MethodDeclaration node) {
		if (currentTypeElement == null) {
			// This can happen for example with Java enum methods
			// TODO Java enum methods not supported.
			// https://stackoverflow.com/questions/18883646/java-enum-methods
			return false;
		}
		JDTElement element = new MethodElement();
		element.node = node;
		element.name = node.getName().getIdentifier();
		StringBuffer qname = new StringBuffer();
		qname.append(currentTypeElement.id);
		qname.append(".");
		qname.append(element.name);
		// add parameters to signature
		qname.append("(");
		List<?> parameters = node.parameters();
		for (Object parameter : parameters) {
			if (parameter instanceof SingleVariableDeclaration) {
				qname.append(((SingleVariableDeclaration) parameter).getType());
				qname.append(",");
			}
		}
		// remove last comma
		if (!parameters.isEmpty()) {
			qname.setLength(qname.length() - 1);
		}
		qname.append(")");

		element.id = qname.toString();
		elements.add(element);

		methodsMap.put(element.id, element);

		// add dependency to the Type
		element.addDependency(DEPENDENCY_TYPE, currentTypeElement);

		// Adding MethodBodyElement
		MethodBodyElement methodBodyElement = new MethodBodyElement();
		methodBodyElement.body = element.node.toString();
		methodBodyElement.addDependency(DEPENDENCY_METHOD_BODY, element);
		elements.add(methodBodyElement);
		int start = cu.getLineNumber(node.getStartPosition());
		int end = cu.getLineNumber(node.getStartPosition() + node.getLength());
		if (lineNumber >= start && lineNumber <= end) {
			this.e = element;
			if (end - start == 0)
				this.totalLines = 1;
			else
				this.totalLines = end - start;
		}
		return true;
	}

	// Imports
	public boolean visit(ImportDeclaration node) {
		JDTElement element = new ImportElement();
		element.node = node;
		element.name = node.getName().getFullyQualifiedName();
		element.id = currentCompilationUnitElement.id + " " + element.name;
		elements.add(element);
		// add dependency to the compilation unit
		element.addDependency(DEPENDENCY_COMPILATION_UNIT, currentCompilationUnitElement);
		int start = cu.getLineNumber(node.getStartPosition());
		int end = cu.getLineNumber(node.getStartPosition() + node.getLength());
		if (lineNumber >= start && lineNumber <= end) {
			this.e = element;
		}
		return true;
	}

	// Fields
	public boolean visit(VariableDeclarationFragment node) {
		// it is a global variable
		if (node.getParent().getParent() instanceof TypeDeclaration) {
			JDTElement element = new FieldElement();
			element.node = node;
			element.name = node.getName().getFullyQualifiedName();
			element.id = currentTypeElement.id + " " + element.name;
			elements.add(element);
			int start = cu.getLineNumber(node.getStartPosition());
			int end = cu.getLineNumber(node.getStartPosition() + node.getLength());
			if (lineNumber >= start && lineNumber <= end) {
				this.e = element;
			}
			// add dependency to the Type
			element.addDependency(DEPENDENCY_TYPE, currentTypeElement);
		}
		return true;
	}

	// Now that we have all the info of elements
	// Adding the dependencies and dependency-related elements
	public void addElementsDependencies() {
		for (IElement element : elements) {
			// Imports
			if (element instanceof ImportElement) {
				JDTElement imported = typesMap.get(((ImportElement) element).name);
				if (imported != null) {
					((AbstractElement) element).addDependency(DEPENDENCY_IMPORTED_TYPE, imported);
				}
			}

			// Extends and Implements
			if (element instanceof TypeElement) {
				TypeElement typeElement = (TypeElement) element;

				// extends
				TypeElement superTypeElement = getSuperTypeElement(typeElement);
				if (superTypeElement != null) {
					TypeExtendsElement typeExtendsElement = new TypeExtendsElement();
					typeExtendsElement.type = typeElement;
					typeExtendsElement.extendedType = superTypeElement;
					typeExtendsElement.addDependency(DEPENDENCY_EXTENDED_TYPE, superTypeElement);
					typeExtendsElement.addDependency(DEPENDENCY_EXTENDS, typeElement);
					newElements.add(typeExtendsElement);
				}

				// implements
				for (TypeElement interfaceTE : getSuperInterfaceTypeElements(typeElement)) {
					TypeImplementsElement typeImplementsElement = new TypeImplementsElement();
					typeImplementsElement.type = typeElement;
					typeImplementsElement.implementedType = interfaceTE;
					typeImplementsElement.addDependency(DEPENDENCY_IMPLEMENTED_TYPE, interfaceTE);
					typeImplementsElement.addDependency(DEPENDENCY_IMPLEMENTS, typeElement);
					newElements.add(typeImplementsElement);
				}
			}
		}

		elements.addAll(newElements);
	}

	/**
	 * Get list of implements type elements
	 * 
	 * @param typeElement
	 * @return list of implements type elements
	 */
	public static List<TypeElement> getSuperInterfaceTypeElements(TypeElement typeElement) {
		List<TypeElement> listInterfaces = new ArrayList<TypeElement>();
		List<?> interfaceTypes = ((TypeDeclaration) typeElement.node).superInterfaceTypes();

		if (interfaceTypes == null || interfaceTypes.isEmpty()) {
			return listInterfaces;
		}

		for (Object o : interfaceTypes) {
			if (o instanceof Type) {
				Type type = (Type) o;
				TypeElement te = getReferencedTypeElementFromATypeElement(type, typeElement);
				if (te != null) {
					listInterfaces.add(te);
				}
			}
		}
		return listInterfaces;
	}

	/**
	 * Get super type element
	 * 
	 * @param typeElement
	 * @return super type element
	 */
	public static TypeElement getSuperTypeElement(TypeElement typeElement) {
		Type superType = ((TypeDeclaration) typeElement.node).getSuperclassType();
		if (superType == null) {
			return null;
		}
		return getReferencedTypeElementFromATypeElement(superType, typeElement);
	}

	/**
	 * Method to know the type element behind a type when it is referenced
	 * inside a type element
	 * 
	 * @param referencedType
	 * @param fromTypeElement
	 * @return
	 */
	public static TypeElement getReferencedTypeElementFromATypeElement(Type referencedType,
			TypeElement fromTypeElement) {
		JDTElement typeElementToReturn = null;
		// using directly a qualified name
		if (referencedType.toString().contains(".")) {
			typeElementToReturn = typesMap.get(referencedType.toString());
		} else {
			// check imports
			CompilationUnit cu = (CompilationUnit) fromTypeElement.node.getRoot();
			for (Object imp : cu.imports()) {
				if (((ImportDeclaration) imp).getName().getFullyQualifiedName()
						.endsWith("." + referencedType.toString())) {
					// found
					typeElementToReturn = typesMap.get(((ImportDeclaration) imp).getName().getFullyQualifiedName());
					break;
				}
			}
		}

		if (typeElementToReturn == null) {
			// try in the same package
			// getPackage, will be only one
			List<IDependencyObject> packages = fromTypeElement.getDependencies().get(DEPENDENCY_PACKAGE);
			if (packages != null && !packages.isEmpty()) {
				PackageElement epackage = (PackageElement) packages.get(0);
				// get all types within the same package
				List<IDependencyObject> types = epackage.getDependants().get(DEPENDENCY_PACKAGE);
				for (IDependencyObject type : types) {
					if (type instanceof TypeElement) {
						if (type != fromTypeElement) {
							typeElementToReturn = typesMap.get(((TypeElement) type).id);
							if (typeElementToReturn != null) {
								return (TypeElement) typeElementToReturn;
							}
						}
					}
				}
			}
		}

		if (typeElementToReturn != null) {
			return (TypeElement) typeElementToReturn;
		}
		return null;
	}

}
