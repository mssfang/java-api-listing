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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jonathangiles.tools.apilisting.model.TokenKind.*;

public class ASTAnalyser implements Analyser {
    private final Map<String, String> knownTypes;
    private int indent;

    public ASTAnalyser() {
        this.indent = 0;
        this.knownTypes = new HashMap<>();
    }

    @Override
    public void analyse(File inputFile, APIListing apiListing) {
        // Root Navigation
        ChildItem rootNavForJar = new ChildItem(inputFile.getName());
        apiListing.addChildItem(rootNavForJar);

        // TODO get all class files from the jar file and process them individually
        // Two phases:
        //   Phase 1: Build up the map of known types
        //   Phase 2: Process all types
        getMethod(inputFile, apiListing, rootNavForJar);
    }

    private void getMethod(File inputFile, APIListing apiListing, ChildItem rootNavForJar) {
        final List<Token> tokens = apiListing.getTokens();
        ParseResult<CompilationUnit> compilationUnitParseResult = null;
        try {
            compilationUnitParseResult = new JavaParser().parse(inputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (compilationUnitParseResult == null) {
            return;
        }
        new ScanForClassTypeVisitor().visit(compilationUnitParseResult.getResult().get(), knownTypes);
        new ClassOrInterfaceVisitor(rootNavForJar).visit(compilationUnitParseResult.getResult().get(), tokens);
    }

    private class ClassOrInterfaceVisitor extends VoidVisitorAdapter {
        private ChildItem parent;

        ClassOrInterfaceVisitor(ChildItem root) {
            parent = root;
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

            enumConstantDeclarations.stream().forEach(enumConstantDeclaration -> {
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
        private void getTypeDeclaration(TypeDeclaration typeDeclaration, List<Token> tokens) {
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

            // Create navigation for this class and add it to the parent
            final String className = typeDeclaration.getNameAsString();
            final String classId = makeId(typeDeclaration.getFullyQualifiedName().get().toString());
            ChildItem classNav = new ChildItem(classId, className, typeKind);
            parent.addChildItem(classNav);
            parent = classNav;

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
                tokens.add(new Token(KEYWORD, "implements"));
                tokens.add(new Token(WHITESPACE, " "));

                for (final ClassOrInterfaceType implementedType : implementedTypes) {
                    getType(implementedType, tokens);
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                }
                if (implementedTypes.size() > 0) {
                    tokens.remove(tokens.size() - 1);
                    tokens.remove(tokens.size() - 1);
                }
            }
            // open ClassOrInterfaceDeclaration
            tokens.add(new Token(WHITESPACE, " "));
            tokens.add(new Token(PUNCTUATION, "{"));
            tokens.add(new Token(NEW_LINE, ""));
        }

        private void getFields(List<FieldDeclaration> fieldDeclarations, List<Token> tokens) {
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

        private void getConstructor(List<ConstructorDeclaration> constructorDeclarations, List<Token> tokens) {
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

        private void getMethods(List<MethodDeclaration> methodDeclarations, List<Token> tokens) {
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
            indent();
            tokens.add(makeWhitespace());
            for (final BodyDeclaration bodyDeclaration : bodyDeclarations) {
                if (bodyDeclaration.isEnumDeclaration() || bodyDeclaration.isClassOrInterfaceDeclaration()) {
                    new ClassOrInterfaceVisitor(parent).visitClassOrInterfaceOrEnumDeclaration(bodyDeclaration.asTypeDeclaration(), tokens);
                }
            }
            unindent();
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
            tokens.add(new Token(WHITESPACE, " "));
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

    private class ScanForClassTypeVisitor extends VoidVisitorAdapter {
        @Override
        public void visit(CompilationUnit compilationUnit, Object arg) {
            for (final TypeDeclaration<?> typeDeclaration : compilationUnit.getTypes()) {
                getTypeDeclaration(typeDeclaration, (Map<String, String>)arg);
            }
        }
        private void getTypeDeclaration(TypeDeclaration typeDeclaration, Map<String, String> knownTypes) {
            // Skip if the class is private or package-private
            if (isPrivateOrPackagePrivate(typeDeclaration.getAccessSpecifier())) {
                return;
            }

            final String fullQualifiedName;

            if (typeDeclaration.isClassOrInterfaceDeclaration()) {
                fullQualifiedName = ((ClassOrInterfaceDeclaration)typeDeclaration).getFullyQualifiedName().get();
            } else if (typeDeclaration.isEnumDeclaration()) {
                fullQualifiedName = ((EnumDeclaration)typeDeclaration).getFullyQualifiedName().get();
            } else {
                fullQualifiedName = null;
            }

            if (fullQualifiedName == null) {
                return;
            }

            knownTypes.put(typeDeclaration.getNameAsString(), makeId(fullQualifiedName));

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
