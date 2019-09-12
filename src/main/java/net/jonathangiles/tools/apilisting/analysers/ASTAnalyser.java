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

import static net.jonathangiles.tools.apilisting.model.TokenKind.*;

public class ASTAnalyser implements Analyser {
    private final Map<String, String> knownTypes;

    public ASTAnalyser() {
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
        new ClassOrInterfaceVisitor(0, rootNavForJar).visit(compilationUnitParseResult.getResult().get(), tokens);
    }

    private class ClassOrInterfaceVisitor extends VoidVisitorAdapter {
        private int indent;
        private ChildItem parent;

        ClassOrInterfaceVisitor(int indent, ChildItem root) {
            this.indent = indent;
            parent = root;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, Object arg) {
            final List<Token> tokens = (List<Token>) arg;

            getClassOrInterface(classOrInterfaceDeclaration, tokens);
            getFields(classOrInterfaceDeclaration, tokens);
            getConstructor(classOrInterfaceDeclaration, tokens);
            getMethods(classOrInterfaceDeclaration, tokens);
            getInnerClass(classOrInterfaceDeclaration, tokens);

            // close class
            tokens.add(makeWhitespace());
            tokens.add(new Token(PUNCTUATION, "}"));
            tokens.add(new Token(NEW_LINE, ""));
        }

        private void getClassOrInterface(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            // Skip if the class is private or package-private
            if (isPrivateOrPackagePrivate(classOrInterfaceDeclaration.getAccessSpecifier())) {
                return;
            }

            getModifiers(classOrInterfaceDeclaration.getModifiers(), tokens);

            TypeKind typeKind;
            if (classOrInterfaceDeclaration.isInterface()) {
                typeKind = TypeKind.INTERFACE;
            } else if (classOrInterfaceDeclaration.isEnumDeclaration()){
                typeKind = TypeKind.ENUM;
            } else {
                typeKind = TypeKind.CLASS;
            }

            final String className = classOrInterfaceDeclaration.getNameAsString();
            final String classId = makeId(classOrInterfaceDeclaration.getFullyQualifiedName().get());
            // Create navigation for this class and add it to the parent
            ChildItem classNav = new ChildItem(classId, className, typeKind);
            parent.addChildItem(classNav);
            parent = classNav;

            tokens.add(new Token(KEYWORD, typeKind.getName()));

            tokens.add(new Token(WHITESPACE, " "));
            tokens.add(new Token(TYPE_NAME, className, classId));
            tokens.add(new Token(WHITESPACE, " "));

            // extends
            final NodeList<ClassOrInterfaceType> extendedTypes = classOrInterfaceDeclaration.getExtendedTypes();
            if (extendedTypes.size() > 0) {
                tokens.add(new Token(STRING_LITERAL, "extends"));
                tokens.add(new Token(WHITESPACE, " "));
                // Java only extends one class
                for (ClassOrInterfaceType extendedType : extendedTypes) {
                    tokens.add(new Token(STRING_LITERAL, extendedType.toString()));
                }
                tokens.add(new Token(WHITESPACE, " "));
            }

            // implements
            final NodeList<ClassOrInterfaceType> implementedTypes = classOrInterfaceDeclaration.getImplementedTypes();
            if (implementedTypes.size() > 0) {
                tokens.add(new Token(STRING_LITERAL, "implements"));
                tokens.add(new Token(WHITESPACE, " "));

                for (final ClassOrInterfaceType implementedType : implementedTypes) {
                    tokens.add(new Token(KEYWORD, implementedType.toString()));
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                }
                if (implementedTypes.size() > 0) {
                    tokens.remove(tokens.size() - 1);
                    tokens.remove(tokens.size() - 1);
                }
                tokens.add(new Token(WHITESPACE, " "));
            }


            // open ClassOrInterfaceDeclaration
            tokens.add(new Token(PUNCTUATION, "{"));
            tokens.add(new Token(NEW_LINE, ""));
        }

        private void getFields(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            indent();
            for (final FieldDeclaration fieldDeclaration : classOrInterfaceDeclaration.getFields()) {
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

        private void getConstructor(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            indent();
            for (final ConstructorDeclaration constructorDeclaration : classOrInterfaceDeclaration.getConstructors()) {
                // Skip if not public
                if (isPrivateOrPackagePrivate(constructorDeclaration.getAccessSpecifier())) {
                    continue;
                }
                tokens.add(makeWhitespace());

                // constructor modifiers: public
                getModifiers(constructorDeclaration.getModifiers(), tokens);

                // type parameters of constructor
                getTypeParameters(constructorDeclaration, tokens);

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

        private void getMethods(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            indent();
            for (final MethodDeclaration methodDeclaration : classOrInterfaceDeclaration.getMethods()) {
                // Skip if not public API
                if (isPrivateOrPackagePrivate(methodDeclaration.getAccessSpecifier())) {
                    continue;
                }

                tokens.add(makeWhitespace());

                // modifiers
                getModifiers(methodDeclaration.getModifiers(), tokens);

                // type parameters of methods
                getTypeParameters(methodDeclaration, tokens);

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

        private void getInnerClass(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            indent();
            for (final BodyDeclaration bodyDeclaration : classOrInterfaceDeclaration.getMembers()) {
                if (bodyDeclaration.isClassOrInterfaceDeclaration()) {
                    tokens.add(makeWhitespace());
                    new ClassOrInterfaceVisitor(indent, parent).visit(bodyDeclaration.asClassOrInterfaceDeclaration(), tokens);
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

        private void getTypeParameters(CallableDeclaration callableDeclaration, List<Token> tokens) {
            final NodeList<TypeParameter> typeParameters = callableDeclaration.getTypeParameters();
            if (typeParameters.size() == 0) {
                return;
            }

            tokens.add(new Token(PUNCTUATION, "<"));
            for (final TypeParameter typeParameter : typeParameters) {
                tokens.add(new Token(TYPE_NAME, typeParameter.asString()));
            }
            tokens.add(new Token(PUNCTUATION, ">"));
            tokens.add(new Token(WHITESPACE, " "));
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

        private String makeId(String fullPath) {
            return fullPath.replaceAll(" ", "-");
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
                // TODO: Map<String,Integer>, List<String>, Supplier<T> all considered as reference type and which we won't able to parse any further as it is ReferenceType
                for (final Node node : type.getChildNodes()) {

                    final List<Node> nodes = node.getChildNodes();
                    if (nodes.size() == 0) {
                        System.out.println("000000");
                        final String typeName = node.toString();
                        final Token token = new Token(TYPE_NAME, typeName);
                        if (knownTypes.containsKey(typeName)) {
                            token.setNavigateToId(knownTypes.get(typeName));
                        }
                        System.out.println("size = 0, token = " + token);
                        tokens.add(token);

                    } else {

                        recursion(node, tokens);
                    }
                }
            } else {
                System.err.println("Unknown type");
            }
        }


        private void recursion(Node node, List<Token> tokens) {

            final List<Node> nodes = node.getChildNodes();
            tokens.add(new Token(PUNCTUATION, "<"));

            for (final Node currentNode : nodes) {
                if (currentNode.getChildNodes().size() == 0) {
                    final String typeName = node.toString();
                    final Token token = new Token(TYPE_NAME, typeName);
                    if (knownTypes.containsKey(typeName)) {
                        token.setNavigateToId(knownTypes.get(typeName));
                    }
                    System.out.println("size = 0+++, token = " + token);
                    tokens.add(token);
                } else if (currentNode.getChildNodes().size() == 1) {
                    final String typeName = currentNode.toString();
                    final Token token = new Token(TYPE_NAME, typeName);
                    if (knownTypes.containsKey(typeName)) {
                        token.setNavigateToId(knownTypes.get(typeName));
                    }
                    System.out.println("size ?= " + currentNode.getChildNodes().size() + " , currentNode = " + currentNode + ", type name = " + typeName);

                    tokens.add(token);
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                } else {
                    System.out.println("size ????= " + currentNode.getChildNodes().size() + " , currentNode = =====" + currentNode );
                    recursion(currentNode, tokens);
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                }


            }
//            tokens.remove(tokens.size() - 1);
//            tokens.remove(tokens.size() - 1);
            tokens.add(new Token(PUNCTUATION, ">"));
        }
    }

    private class ScanForClassTypeVisitor extends VoidVisitorAdapter {
        @Override
        public void visit(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, Object arg) {
            Map<String, String> knownTypes = (Map<String, String>)arg;
            getClassOrInterface(classOrInterfaceDeclaration, knownTypes);
        }

        private void getClassOrInterface(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, Map<String, String> knownTypes) {
            // Skip if the class is private or package-private
            if (isPrivateOrPackagePrivate(classOrInterfaceDeclaration.getAccessSpecifier())) {
                return;
            }

            knownTypes.put(classOrInterfaceDeclaration.getNameAsString(),
                    makeId(classOrInterfaceDeclaration.getFullyQualifiedName().get()));

            for (final BodyDeclaration bodyDeclaration : classOrInterfaceDeclaration.getMembers()) {
                if (bodyDeclaration.isClassOrInterfaceDeclaration()) {
                    new ScanForClassTypeVisitor().visit(bodyDeclaration.asClassOrInterfaceDeclaration(), knownTypes);
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
    }
}
