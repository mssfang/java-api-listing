package net.jonathangiles.tools.apilisting.analysers;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import net.jonathangiles.tools.apilisting.model.APIListing;
import net.jonathangiles.tools.apilisting.model.ChildItem;
import net.jonathangiles.tools.apilisting.model.Token;
import net.jonathangiles.tools.apilisting.model.TypeKind;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static net.jonathangiles.tools.apilisting.model.TokenKind.*;

public class ASTAnalyser implements Analyser {
    // a map of type name to unique identifier, used for navigation
    private final Map<String, String> knownTypes;

    // a map of package names to a list of types within that package
    private final Map<String, List<String>> packageNamesToTypesMap;

    private final Map<String, ChildItem> packageNameToNav;

    private int indent;

    public ASTAnalyser() {
        this.indent = 0;
        this.knownTypes = new HashMap<>();
        this.packageNamesToTypesMap = new HashMap<>();
        this.packageNameToNav = new HashMap<>();
    }

    public void analyse(List<Path> allFiles, File tempDir, APIListing apiListing) {
        // we build a custom classloader so that we can load classes that were not on the classpath
        String rootDirectory = tempDir.getPath();

        ClassLoader classLoader = null;
        try {
            URL url = new File(rootDirectory).toURI().toURL();
            URL[] urls = new URL[] {url};
            classLoader = URLClassLoader.newInstance(urls);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        final ClassLoader cl = classLoader;

        // firstly we filter out the files we don't care about
        allFiles = allFiles.stream()
           .filter(path -> {
               File inputFile = path.toFile();
               String inputFileName = inputFile.toString();
               if (inputFile.isDirectory()) return false;
               else if (inputFileName.contains("implementation")) return false;
               else if (inputFileName.equals("package-info.java")) return false;
               else if (!inputFileName.endsWith(".java")) return false;
               else return true;
           }).collect(Collectors.toList());

        // then we do a pass to build a map of all known types and package names, and a map of package names to nav items,
        // followed by a pass to tokenise each file
        allFiles.stream()
                .map(path -> scanForTypes(path, cl))
                .collect(Collectors.toList())
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted((s1, s2) -> s1.path.compareTo(s2.path))
                .forEach(scanClass -> processSingleFile(scanClass, apiListing));

        // build the navigation
        packageNameToNav.values().stream().sorted(Comparator.comparing(ChildItem::getText)).forEach(apiListing::addChildItem);
    }

    private static class ScanClass {
        private ParseResult<CompilationUnit> parseResult;
        private Path path;

        public ScanClass(Path path, ParseResult<CompilationUnit> parseResult) {
            this.parseResult = parseResult;
            this.path = path;
        }
    }

    private Optional<ScanClass> scanForTypes(Path path, ClassLoader classLoader) {
        File inputFile = path.toFile();

        try {
            ParseResult<CompilationUnit> parseResult = new JavaParser().parse(inputFile);
            new ScanForClassTypeVisitor().visit(parseResult.getResult().get(), knownTypes);
            return Optional.of(new ScanClass(path, parseResult));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private void processSingleFile(ScanClass scanClass, APIListing apiListing) {
        new ClassOrInterfaceVisitor().visit(scanClass.parseResult.getResult().get(), apiListing.getTokens());
    }

    private class ClassOrInterfaceVisitor extends VoidVisitorAdapter {
        private ChildItem parentNav;

        public ClassOrInterfaceVisitor() {   }

        ClassOrInterfaceVisitor(ChildItem parentNav) {
            this.parentNav = parentNav;
        }

        @Override
        public void visit(CompilationUnit compilationUnit, Object arg) {
            final List<Token> tokens = (List<Token>) arg;

            NodeList<TypeDeclaration<?>> types = compilationUnit.getTypes();
            for (final TypeDeclaration<?> typeDeclaration : types) {
                visitClassOrInterfaceOrEnumDeclaration(typeDeclaration, tokens);
            }
        }

        private void visitClassOrInterfaceOrEnumDeclaration(TypeDeclaration<?> typeDeclaration, List<Token> tokens) {
            getTypeDeclaration(typeDeclaration, tokens);

            if (typeDeclaration.isEnumDeclaration()) {
                getEnumEntries(((EnumDeclaration)typeDeclaration).getEntries(), tokens);
            }

            getFields(typeDeclaration.getFields(), tokens);
            getConstructor(typeDeclaration.getConstructors(), tokens);
            getMethods(typeDeclaration.getMethods(), tokens);
            getInnerClass(typeDeclaration.getMembers(), tokens);

            // close class
            tokens.add(makeWhitespace());
            tokens.add(new Token(PUNCTUATION, "}"));
            tokens.add(new Token(NEW_LINE, ""));
        }

        private void getEnumEntries(NodeList<EnumConstantDeclaration> enumConstantDeclarations, List<Token> tokens) {
            int size = enumConstantDeclarations.size();
            indent();

            AtomicInteger counter = new AtomicInteger();

            enumConstantDeclarations.forEach(enumConstantDeclaration -> {
                tokens.add(makeWhitespace());
                tokens.add(new Token(MEMBER_NAME, enumConstantDeclaration.getNameAsString()));

                enumConstantDeclaration.getArguments().stream().forEach(expression -> {
                    tokens.add(new Token(PUNCTUATION, "("));
                    tokens.add(new Token(TEXT, expression.toString()));
                    tokens.add(new Token(PUNCTUATION, ")"));
                });

                if (counter.getAndIncrement() < size - 1) {
                    tokens.add(new Token(PUNCTUATION, ","));
                } else {
                    tokens.add(new Token(PUNCTUATION, ";"));
                }
                tokens.add(new Token(NEW_LINE, ""));
            });

            unindent();
        }
        
        private void getTypeDeclaration(TypeDeclaration<?> typeDeclaration, List<Token> tokens) {
            // Skip if the class is private or package-private
            if (isPrivateOrPackagePrivate(typeDeclaration.getAccessSpecifier())) {
                return;
            }

            // Get modifiers
            getModifiers(typeDeclaration.getModifiers(), tokens);

            // Get type kind
            TypeKind typeKind;
            if (typeDeclaration.isClassOrInterfaceDeclaration()) {
                typeKind = ((ClassOrInterfaceDeclaration)typeDeclaration).isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
            } else if (typeDeclaration.isEnumDeclaration()) {
                typeKind = TypeKind.ENUM;
            } else {
                typeKind = TypeKind.UNKNOWN;
            }

            String fullQualifiedName = typeDeclaration.getFullyQualifiedName().get();

            // Create navigation for this class and add it to the parent
            final String className = typeDeclaration.getNameAsString();
            final String packageName = fullQualifiedName.substring(0, fullQualifiedName.lastIndexOf("."));
            final String classId = makeId(typeDeclaration.getFullyQualifiedName().get());
            ChildItem classNav = new ChildItem(classId, className, typeKind);
            if (parentNav == null) {
                packageNameToNav.get(packageName).addChildItem(classNav);
            } else {
                parentNav.addChildItem(classNav);
            }
//            parent.addChildItem(classNav);
            parentNav = classNav;

            tokens.add(new Token(KEYWORD, typeKind.getName()));
            tokens.add(new Token(WHITESPACE, " "));
            tokens.add(new Token(TYPE_NAME, className, classId));

            NodeList<ClassOrInterfaceType> implementedTypes = null;
            // Type parameters of class definition
            if (typeDeclaration.isClassOrInterfaceDeclaration()) {
                final ClassOrInterfaceDeclaration classOrInterfaceDeclaration = (ClassOrInterfaceDeclaration)typeDeclaration;

                // Get type parameters
                getTypeParameters(classOrInterfaceDeclaration.getTypeParameters(), tokens);

                // Extends a class
                final NodeList<ClassOrInterfaceType> extendedTypes = classOrInterfaceDeclaration.getExtendedTypes();
                if (extendedTypes.size() > 0) {
                    tokens.add(new Token(WHITESPACE, " "));
                    tokens.add(new Token(KEYWORD, "extends"));
                    tokens.add(new Token(WHITESPACE, " "));
                    // Java only extends one class
                    for (ClassOrInterfaceType extendedType : extendedTypes) {
                        getType(extendedType, tokens);
                    }
                }
                // Assign implement types
                implementedTypes = classOrInterfaceDeclaration.getImplementedTypes();
            } else if (typeDeclaration.isEnumDeclaration()) {
                final EnumDeclaration enumDeclaration = (EnumDeclaration)typeDeclaration;
                // Assign implement types
                implementedTypes = enumDeclaration.getImplementedTypes();
            } else {
                System.err.println("Not a class, interface or enum declaration");
            }

            // implements interfaces
            if (implementedTypes != null && implementedTypes.size() > 0) {
                tokens.add(new Token(WHITESPACE, " "));
                tokens.add(new Token(KEYWORD, "implements"));
                tokens.add(new Token(WHITESPACE, " "));

                for (final ClassOrInterfaceType implementedType : implementedTypes) {
                    getType(implementedType, tokens);
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                }
                if (!implementedTypes.isEmpty()) {
                    tokens.remove(tokens.size() - 1);
                    tokens.remove(tokens.size() - 1);
                }
            }
            // open ClassOrInterfaceDeclaration
            tokens.add(new Token(WHITESPACE, " "));
            tokens.add(new Token(PUNCTUATION, "{"));
            tokens.add(new Token(NEW_LINE, ""));
        }

        private void getFields(List<? extends FieldDeclaration> fieldDeclarations, List<Token> tokens) {
            indent();
            for ( FieldDeclaration fieldDeclaration : fieldDeclarations) {
                // Skip if it is private or package-private field
                if (isPrivateOrPackagePrivate(fieldDeclaration.getAccessSpecifier())) {
                    continue;
                }

                tokens.add(makeWhitespace());

                final NodeList<Modifier> fieldModifiers = fieldDeclaration.getModifiers();
                // public, protected, static, final
                for (final Modifier fieldModifier: fieldModifiers) {
                    tokens.add(new Token(KEYWORD, fieldModifier.toString()));
                }

                // field type and name
                final NodeList<VariableDeclarator> variableDeclarators = fieldDeclaration.getVariables();
                if (variableDeclarators.size() > 1) {
                    getType(fieldDeclaration, tokens);

                    for (VariableDeclarator variableDeclarator : variableDeclarators) {
                        tokens.add(new Token(MEMBER_NAME, variableDeclarator.getNameAsString()));
                        tokens.add(new Token(PUNCTUATION, ","));
                        tokens.add(new Token(WHITESPACE, " "));
                    }
                    tokens.remove(tokens.size() - 1);
                    tokens.remove(tokens.size() - 1);

                } else if (variableDeclarators.size() == 1) {
                    getType(fieldDeclaration, tokens);
                    final VariableDeclarator variableDeclarator = variableDeclarators.get(0);
                    tokens.add(new Token(MEMBER_NAME, variableDeclarator.getNameAsString()));

                    final Optional<Expression> variableDeclaratorOption = variableDeclarator.getInitializer();
                    if (variableDeclaratorOption.isPresent()) {
                        tokens.add(new Token(WHITESPACE, " "));
                        tokens.add(new Token(PUNCTUATION, "="));
                        tokens.add(new Token(WHITESPACE, " "));
                        tokens.add(new Token(TEXT, variableDeclaratorOption.get().toString()));
                    }
                } else {
                    // will not run at here
                }

                // close the variable declaration
                tokens.add(new Token(PUNCTUATION, ";"));
                tokens.add(new Token(NEW_LINE, ""));
            }
            unindent();
        }

        private void getConstructor(List<? extends ConstructorDeclaration> constructorDeclarations, List<Token> tokens) {
            indent();
            for (final ConstructorDeclaration constructorDeclaration : constructorDeclarations) {
                // Skip if not public
                if (isPrivateOrPackagePrivate(constructorDeclaration.getAccessSpecifier())) {
                    continue;
                }
                tokens.add(makeWhitespace());

                // constructor modifiers: public
                getModifiers(constructorDeclaration.getModifiers(), tokens);

                // type parameters of constructor
                getTypeParameters(constructorDeclaration.getTypeParameters(), tokens);

                // constructor name and parameters
                getDeclarationNameAndParameters(constructorDeclaration, constructorDeclaration.getParameters(), tokens);

                // constructor throws Exceptions
                getThrowException(constructorDeclaration, tokens);

                // close statements
                tokens.add(new Token(PUNCTUATION, "{"));
                tokens.add(new Token(PUNCTUATION, "}"));
                tokens.add(new Token(NEW_LINE, ""));
            }
            unindent();
        }

        private void getMethods(List<? extends MethodDeclaration> methodDeclarations, List<Token> tokens) {
            indent();
            for (final MethodDeclaration methodDeclaration : methodDeclarations) {
                // Skip if not public API
                if (isPrivateOrPackagePrivate(methodDeclaration.getAccessSpecifier())) {
                    continue;
                }

                tokens.add(makeWhitespace());

                // modifiers
                getModifiers(methodDeclaration.getModifiers(), tokens);

                // type parameters of methods
                getTypeParameters(methodDeclaration.getTypeParameters(), tokens);

                // type name
                getType(methodDeclaration, tokens);

                // method name and parameters
                getDeclarationNameAndParameters(methodDeclaration, methodDeclaration.getParameters(), tokens);

                // throw exceptions
                getThrowException(methodDeclaration, tokens);

                // close statements
                tokens.add(new Token(PUNCTUATION, "{"));
                tokens.add(new Token(PUNCTUATION, "}"));
                tokens.add(new Token(NEW_LINE, ""));
            }
            unindent();
        }

        private void getInnerClass(NodeList<BodyDeclaration<?>> bodyDeclarations, List<Token> tokens) {
            for (final BodyDeclaration bodyDeclaration : bodyDeclarations) {
                if (bodyDeclaration.isEnumDeclaration() || bodyDeclaration.isClassOrInterfaceDeclaration()) {
                    indent();
                    tokens.add(makeWhitespace());
                    new ClassOrInterfaceVisitor(parentNav).visitClassOrInterfaceOrEnumDeclaration(bodyDeclaration.asTypeDeclaration(), tokens);
                    unindent();
                }
            }
        }

        private void getModifiers(NodeList<Modifier> modifiers, List<Token> tokens) {
            for (final Modifier modifier : modifiers) {
                tokens.add(new Token(KEYWORD, modifier.toString()));
            }
        }

        private boolean isPrivateOrPackagePrivate(AccessSpecifier accessSpecifier) {
            return accessSpecifier.equals(AccessSpecifier.PRIVATE)
                    || accessSpecifier.equals(AccessSpecifier.PACKAGE_PRIVATE);
        }

        private void getDeclarationNameAndParameters(CallableDeclaration callableDeclaration, NodeList<Parameter> parameters, List<Token> tokens) {
            String name = callableDeclaration.getNameAsString();

            String definitionId = callableDeclaration.getDeclarationAsString().replaceAll(" ", "-");
            tokens.add(new Token(MEMBER_NAME, name, definitionId));

            tokens.add(new Token(PUNCTUATION, "("));
            if (parameters.size() > 0) {
                for (final Parameter parameter : parameters) {
                    getType(parameter, tokens);
                    tokens.add(new Token(WHITESPACE, " "));
                    tokens.add(new Token(TEXT, parameter.getNameAsString()));
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                }
                tokens.remove(tokens.size() - 1);
                tokens.remove(tokens.size() - 1);
            }
            // close declaration
            tokens.add(new Token(PUNCTUATION, ")"));
            tokens.add(new Token(WHITESPACE, " "));
        }

        private void getTypeParameters(NodeList<TypeParameter> typeParameters, List<Token> tokens) {
            final int size = typeParameters.size();
            if (size == 0) {
                return;
            }
            tokens.add(new Token(PUNCTUATION, "<"));
            for (int i = 0; i < size; i++) {
                final TypeParameter typeParameter = typeParameters.get(i);
                getGenericTypeParameter(typeParameter, tokens);
                if (i != size - 1) {
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                }
            }
            tokens.add(new Token(PUNCTUATION, ">"));
        }

        private void getGenericTypeParameter(TypeParameter typeParameter, List<Token> tokens) {
            // set navigateToId
            final String typeName = typeParameter.getNameAsString();
            final Token token = new Token(TYPE_NAME, typeName);
            if (knownTypes.containsKey(typeName)) {
                token.setNavigateToId(knownTypes.get(typeName));
            }
            tokens.add(token);
            // get type bounds
            final NodeList<ClassOrInterfaceType> typeBounds = typeParameter.getTypeBound();
            final int size = typeBounds.size();
            if (size != 0) {
                tokens.add(new Token(WHITESPACE, " "));
                tokens.add(new Token(KEYWORD, "extends"));
                tokens.add(new Token(WHITESPACE, " "));
                for (int i = 0; i < size; i++) {
                    getType(typeBounds.get(i), tokens);
                }
            }
        }

        private void getThrowException(CallableDeclaration callableDeclaration, List<Token> tokens) {
            final NodeList<ReferenceType> thrownExceptions = callableDeclaration.getThrownExceptions();
            if (thrownExceptions.size() == 0) {
                return;
            }

            tokens.add(new Token(STRING_LITERAL, "throws"));
            tokens.add(new Token(WHITESPACE, " "));

            for (final ReferenceType referenceType : thrownExceptions) {
                tokens.add(new Token(TYPE_NAME, referenceType.getElementType().toString()));
                tokens.add(new Token(PUNCTUATION, ","));
                tokens.add(new Token(WHITESPACE, " "));
            }
            tokens.remove(tokens.size() - 1);
            tokens.remove(tokens.size() - 1);
            tokens.add(new Token(WHITESPACE, " "));
        }

        private void getType(Object type, List<Token> tokens) {
            if (type instanceof Parameter) {
                final Parameter parameterType = (Parameter) type;
                getClassType(parameterType.getType(), tokens);
            } else if (type instanceof MethodDeclaration) {
                getClassType(((MethodDeclaration)type).getType(), tokens);
                tokens.add(new Token(WHITESPACE, " "));
            } else if (type instanceof FieldDeclaration) {
                getClassType(((FieldDeclaration)type).getElementType(), tokens);
                tokens.add(new Token(WHITESPACE, " "));
            } else if (type instanceof ClassOrInterfaceType) {
                getClassType(((ClassOrInterfaceType)type), tokens);
            } else {
                System.err.println("Unknown type " + type + " of type " + type.getClass());
            }
        }

        private void getClassType(Type type, List<Token> tokens) {
            if (type.isArrayType()) {
                getClassType(type.getElementType(), tokens);
                //TODO: need to correct int[][] scenario
                tokens.add(new Token(PUNCTUATION, "[]"));
            } else if (type.isPrimitiveType() || type.isVoidType()) {
                tokens.add(new Token(TYPE_NAME, type.toString()));
            } else if (type.isReferenceType() || type.isTypeParameter() || type.isWildcardType()) {
                getTypeDFS(type, tokens);
            } else {
                System.err.println("Unknown type");
            }
        }

        private void getTypeDFS(Node node, List<Token> tokens) {
            final List<Node> nodes = node.getChildNodes();
            final int childrenSize = nodes.size();
            if (childrenSize <= 1) {
                final String typeName = node.toString();
                final Token token = new Token(TYPE_NAME, typeName);
                if (knownTypes.containsKey(typeName)) {
                    token.setNavigateToId(knownTypes.get(typeName));
                }
                tokens.add(token);
                return;
            }

            for (int i = 0; i < childrenSize; i++) {
                final Node currentNode = nodes.get(i);

                if (i == 1) {
                    tokens.add(new Token(PUNCTUATION, "<"));
                }

                getTypeDFS(currentNode, tokens);

                if (i != 0 && i != childrenSize - 1) {
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                }

                if (i != 0 && i == childrenSize - 1) {
                    tokens.add(new Token(PUNCTUATION, ">"));
                }
            }
        }
    }

    private class ScanForClassTypeVisitor extends VoidVisitorAdapter<Map<String, String>> {
        @Override
        public void visit(CompilationUnit compilationUnit, Map<String, String> arg) {
            for (final TypeDeclaration<?> typeDeclaration : compilationUnit.getTypes()) {
                getTypeDeclaration(typeDeclaration, arg);
            }
        }

        private void getTypeDeclaration(TypeDeclaration<?> typeDeclaration, Map<String, String> knownTypes) {
            // Skip if the class is private or package-private
            if (isPrivateOrPackagePrivate(typeDeclaration.getAccessSpecifier())) {
                return;
            }

            if (! (typeDeclaration.isClassOrInterfaceDeclaration() || typeDeclaration.isEnumDeclaration())) {
                return;
            }

            final String fullQualifiedName = typeDeclaration.getFullyQualifiedName().get();

            // determine the package name for this class
            String typeName = typeDeclaration.getNameAsString();
            String packageName = fullQualifiedName.substring(0, fullQualifiedName.lastIndexOf("."));
            packageNamesToTypesMap.computeIfAbsent(packageName, name -> new ArrayList<>()).add(typeName);

            // generate a navigation item for each new package, but we don't add them to the parent yet
            packageNameToNav.computeIfAbsent(packageName, name -> new ChildItem(packageName));

            knownTypes.put(typeName, makeId(fullQualifiedName));

            for (final Object bodyDeclaration : typeDeclaration.getMembers()) {
                BodyDeclaration bodyDeclarationMember = (BodyDeclaration)bodyDeclaration;
                if (bodyDeclarationMember.isEnumDeclaration() || bodyDeclarationMember.isClassOrInterfaceDeclaration()) {
                    getTypeDeclaration(bodyDeclarationMember.asTypeDeclaration(), knownTypes);
                }
            }
        }
    }

    private boolean isPrivateOrPackagePrivate(AccessSpecifier accessSpecifier) {
        return accessSpecifier.equals(AccessSpecifier.PRIVATE)
                || accessSpecifier.equals(AccessSpecifier.PACKAGE_PRIVATE);
    }

    private String makeId(String fullPath) {
        return fullPath.replaceAll(" ", "-");
    }

    private void indent() {
        indent += 4;
    }

    private void unindent() {
        indent = Math.max(indent - 4, 0);
    }

    private Token makeWhitespace() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append(" ");
        }
        return new Token(WHITESPACE, sb.toString());
    }
}
