package net.jonathangiles.tools.apilisting.analysers;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import net.jonathangiles.tools.apilisting.model.APIListing;
import net.jonathangiles.tools.apilisting.model.ChildItem;
import net.jonathangiles.tools.apilisting.model.Token;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;

import static net.jonathangiles.tools.apilisting.model.TokenKind.*;

public class ASTAnalyser implements Analyser {
    @Override
    public void analyse(File inputFile, APIListing apiListing) {
        // Root Navigation
        ChildItem rootNavForJar = new ChildItem(inputFile.getName());
        apiListing.addChildItem(rootNavForJar);

        // TODO get all class files from the jar file and process them individually
        getMethod(inputFile, apiListing);
    }


    private void getMethod(File inputFile, APIListing apiListing) {
        List<Token> tokens = apiListing.getTokens();
        ParseResult<CompilationUnit> compilationUnitParseResult = null;
        try {
            compilationUnitParseResult = new JavaParser().parse(inputFile);
        } catch (FileNotFoundException err) {
            System.out.println("File cannot be parsed!!!");
        }

        if (compilationUnitParseResult == null) {
            return;
        }

        CompilationUnit compilationUnit = compilationUnitParseResult.getResult().get();

        // Visit class or interface declaration
        new ClassOrInterfaceVisitor().visit(compilationUnit, tokens);
    }

    private static class ClassOrInterfaceVisitor extends VoidVisitorAdapter {
        @Override
        public void visit(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, Object arg) {
            final List<Token> tokens = (List<Token>) arg;
            // Skip if the class is private or package-private
            if (isPrivateOrPackagePrivate(classOrInterfaceDeclaration.getAccessSpecifier())) {
                return;
            }

            getModifiers(classOrInterfaceDeclaration.getModifiers(), tokens);
            getClassOrInterface(classOrInterfaceDeclaration, tokens);
            getFields(classOrInterfaceDeclaration, tokens);
            getConstructor(classOrInterfaceDeclaration, tokens);
            getMethods(classOrInterfaceDeclaration, tokens);

            // close class
            tokens.add(new Token(PUNCTUATION, "}"));
            tokens.add(new Token(NEW_LINE, ""));
        }

        private void getModifiers(NodeList<Modifier> modifiers, List<Token> tokens) {
            for (Modifier modifier : modifiers) {
                tokens.add(new Token(KEYWORD, modifier.toString()));
            }
        }

        private boolean isPrivateOrPackagePrivate(AccessSpecifier accessSpecifier) {
            return accessSpecifier.equals(AccessSpecifier.PRIVATE)
                    || accessSpecifier.equals(AccessSpecifier.PACKAGE_PRIVATE);
        }

        private void getDeclarationNameAndParameters(CallableDeclaration callableDeclaration, NodeList<Parameter> parameters, List<Token> tokens) {
            tokens.add(new Token(STRING_LITERAL, callableDeclaration.getNameAsString()));
            tokens.add(new Token(PUNCTUATION, "("));
            if (parameters.size() > 0 ) {
                for (final Parameter parameter : parameters) {
                    tokens.add(new Token(TYPE_NAME, parameter.getTypeAsString()));
                    tokens.add(new Token(WHITESPACE, " "));
                    tokens.add(new Token(KEYWORD, parameter.getNameAsString()));
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

        private void getClassOrInterface(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            if (classOrInterfaceDeclaration.isInterface()) {
                tokens.add(new Token(STRING_LITERAL, "interface"));
            } else {
                tokens.add(new Token(STRING_LITERAL, "class"));
            }
            tokens.add(new Token(WHITESPACE, " "));
            tokens.add(new Token(STRING_LITERAL, classOrInterfaceDeclaration.getNameAsString()));
            tokens.add(new Token(WHITESPACE, " "));

            // implements
            NodeList<ClassOrInterfaceType> implementedTypes = classOrInterfaceDeclaration.getImplementedTypes();
            if (implementedTypes.size() > 0) {
                tokens.add(new Token(STRING_LITERAL, "implements"));
                tokens.add(new Token(WHITESPACE, " "));

                for (ClassOrInterfaceType implementedType : implementedTypes) {
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

            // extends
            NodeList<ClassOrInterfaceType> extendedTypes = classOrInterfaceDeclaration.getExtendedTypes();
            if (extendedTypes.size() > 0) {
                tokens.add(new Token(STRING_LITERAL, "extends"));
                tokens.add(new Token(WHITESPACE, " "));
                // Java only extends one class
                for (ClassOrInterfaceType extendedType : extendedTypes) {
                    tokens.add(new Token(STRING_LITERAL, extendedType.toString()));
                }
                tokens.add(new Token(WHITESPACE, " "));
            }
            // open ClassOrInterfaceDeclaration
            tokens.add(new Token(PUNCTUATION, "{"));
            tokens.add(new Token(NEW_LINE, ""));
        }

        private void getFields(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            List<FieldDeclaration> fieldDeclarations = classOrInterfaceDeclaration.getFields();
            for (final FieldDeclaration fieldDeclaration : fieldDeclarations) {

                // Skip if it is private or package-private field
                final AccessSpecifier fieldAccessSpecifier = fieldDeclaration.getAccessSpecifier();
                if (fieldAccessSpecifier.equals(AccessSpecifier.PRIVATE)
                        || fieldAccessSpecifier.equals(AccessSpecifier.PACKAGE_PRIVATE)) {
                    continue;
                }

                //                System.out.println("fields = " + fieldDeclaration.toString());
                final NodeList<Modifier> fieldModifiers = fieldDeclaration.getModifiers();
                // public, protected, static, final
                for (final Modifier fieldModifier: fieldModifiers) {
//                    System.out.println("Field Modifier = " + fieldModifier.toString());
                    tokens.add(new Token(KEYWORD, fieldModifier.toString()));
                }

                // type of variable


                // vars name
                final NodeList<VariableDeclarator> variableDeclarators = fieldDeclaration.getVariables();
                if (variableDeclarators.size() > 1) {
                    tokens.add(new Token(KEYWORD, fieldDeclaration.getElementType().toString()));
                    tokens.add(new Token(WHITESPACE, " "));

                    for (VariableDeclarator variableDeclarator : variableDeclarators) {
                        tokens.add(new Token(KEYWORD, variableDeclarator.getName().toString()));
                        tokens.add(new Token(PUNCTUATION, ","));
                        tokens.add(new Token(WHITESPACE, " "));

                    }
                    tokens.remove(tokens.size() - 1);
                    tokens.remove(tokens.size() - 1);

                } else if (variableDeclarators.size() == 1) {
                    tokens.add(new Token(KEYWORD, fieldDeclaration.getElementType().toString()));
                    tokens.add(new Token(WHITESPACE, " "));
                    final VariableDeclarator variableDeclarator = variableDeclarators.get(0);
                    tokens.add(new Token(KEYWORD, variableDeclarator.getName().toString()));

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
        }

        private void getConstructor(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            for (ConstructorDeclaration constructorDeclaration : classOrInterfaceDeclaration.getConstructors()) {
                // Skip if not public
                if (isPrivateOrPackagePrivate(constructorDeclaration.getAccessSpecifier())) {
                    continue;
                }
                // constructor modifiers: public
                getModifiers(constructorDeclaration.getModifiers(), tokens);
                // constructor name and parameters
                getDeclarationNameAndParameters(constructorDeclaration, constructorDeclaration.getParameters(), tokens);

                // constructor throws Exceptions
                // TODO: add throws Exception
//                final NodeList<ReferenceType> referenceTypes = constructorDeclaration.getThrownExceptions();
//                for (final ReferenceType referenceType : referenceTypes) {
//
//                }


                // close statements
                tokens.add(new Token(PUNCTUATION, "{"));
                tokens.add(new Token(PUNCTUATION, "}"));
                tokens.add(new Token(NEW_LINE, ""));

                //TODO: research TypeParameter in java https://static.javadoc.io/com.github.javaparser/javaparser-core/3.2.10/com/github/javaparser/ast/type/TypeParameter.html
//                constructorDeclaration.getTypeParameters();
            }
        }

        private void getMethods(ClassOrInterfaceDeclaration classOrInterfaceDeclaration, List<Token> tokens) {
            final List<MethodDeclaration> methodDeclarations = classOrInterfaceDeclaration.getMethods();
            for (final MethodDeclaration methodDeclaration : methodDeclarations) {
                // Skip if not public API
                if (isPrivateOrPackagePrivate(methodDeclaration.getAccessSpecifier())) {
                    continue;
                }
                // modifiers
                getModifiers(methodDeclaration.getModifiers(), tokens);
                // type name
                tokens.add(new Token(TYPE_NAME, methodDeclaration.getTypeAsString()));
                tokens.add(new Token(WHITESPACE, " "));
                // method name and parameters
                getDeclarationNameAndParameters(methodDeclaration, methodDeclaration.getParameters(), tokens);

                // TODO: throws exception

                // close statements
                tokens.add(new Token(PUNCTUATION, "{"));
                tokens.add(new Token(PUNCTUATION, "}"));
                tokens.add(new Token(NEW_LINE, ""));
            }
        }
    }
}

