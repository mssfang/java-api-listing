package net.jonathangiles.tools.apilisting.analysers;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.metamodel.FieldDeclarationMetaModel;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.sun.org.apache.xpath.internal.operations.Mod;
import net.jonathangiles.tools.apilisting.model.APIListing;
import net.jonathangiles.tools.apilisting.model.ChildItem;
import net.jonathangiles.tools.apilisting.model.Token;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import static net.jonathangiles.tools.apilisting.model.TokenKind.KEYWORD;
import static net.jonathangiles.tools.apilisting.model.TokenKind.NEW_LINE;
import static net.jonathangiles.tools.apilisting.model.TokenKind.PUNCTUATION;
import static net.jonathangiles.tools.apilisting.model.TokenKind.WHITESPACE;

public class ASTAnalyser implements Analyser {
//    List<Token> tokens;
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
            List<Token> tokens = (List<Token>) arg;
            NodeList<Modifier> modifiers = classOrInterfaceDeclaration.getModifiers();
            AccessSpecifier classAccessSpecifier = classOrInterfaceDeclaration.getAccessSpecifier();
            // Skip if the class has private or package-private accessibility
            if (classAccessSpecifier.equals(AccessSpecifier.PRIVATE)
                    || classAccessSpecifier.equals(AccessSpecifier.PACKAGE_PRIVATE)) {
                return;
            }
            // add modifiers
            for (Modifier modifier : modifiers) {
//                System.out.println(" modifier  = "  + modifier);
                tokens.add(new Token(KEYWORD, modifier.toString()));
            }
            // Class or interface
            if (classOrInterfaceDeclaration.isInterface()) {
                tokens.add(new Token(KEYWORD, "interface"));
            } else {
                tokens.add(new Token(KEYWORD, "class"));
            }
            tokens.add(new Token(WHITESPACE, " "));

            // name of class or interface
//            System.out.println("class or interface name = " + classOrInterfaceDeclaration.getName().toString());
            tokens.add(new Token(KEYWORD, classOrInterfaceDeclaration.getName().toString()));
            tokens.add(new Token(WHITESPACE, " "));

            // implements
            NodeList<ClassOrInterfaceType> implementedTypes = classOrInterfaceDeclaration.getImplementedTypes();
//            System.out.println("implementedType = " + implementedTypes.toString());
            if (implementedTypes.size() > 0) {
                tokens.add(new Token(KEYWORD, "implements"));
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
                tokens.add(new Token(KEYWORD, "extends"));
                tokens.add(new Token(WHITESPACE, " "));
//                System.out.println("extended type = " + extendedTypes.toString());

                // Java only extends one class
                for (ClassOrInterfaceType extendedType : extendedTypes) {
                    tokens.add(new Token(KEYWORD, extendedType.toString()));
                }
                tokens.add(new Token(WHITESPACE, " "));
            }

            tokens.add(new Token(PUNCTUATION, "{"));
            tokens.add(new Token(NEW_LINE, ""));

            // Fields
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
                    tokens.add(new Token(KEYWORD, variableDeclarators.get(0).getName().toString()));
                } else {
                    // will not run at here
                }

                // assignment
                System.out.println("size  = " + fieldDeclaration.getDataKeys().size());
                for (DataKey data : fieldDeclaration.getDataKeys()) {
                    System.out.println("data = = " + data.toString());
                }
//                fieldDeclaration.getData(new DataKey<String>());


                // close the variable declaration
                tokens.add(new Token(PUNCTUATION, ";"));
                tokens.add(new Token(NEW_LINE, ""));
            }


            // Constructors
//            System.out.println("constructor = " + classOrInterfaceDeclaration.getConstructors().toString());

            // Methods
//            System.out.println("methods = " + classOrInterfaceDeclaration.getMethods().toString());


            // close class
            tokens.add(new Token(PUNCTUATION, "}"));
            tokens.add(new Token(NEW_LINE, ""));

            ////////////////////////////////////////////////////////////////////////////////////

//            System.out.println("type parameters = " + classOrInterfaceDeclaration.getTypeParameters().toString());
        }
    }






//    private static class MethodVisitor extends VoidVisitorAdapter {
//        @Override
//        public void visit(MethodDeclaration methodDeclaration, Object arg) {
//            List<Token> tokens = (List<Token>) arg;
//
//            tokens.add(new Token(KEYWORD, "class"));
//        }
//    }
//    private static class FieldVisitor extends VoidVisitorAdapter {
//        @Override
//        public void visit(FieldDeclaration fieldDeclaration, Object arg) {
//            List<Token> tokens = (List<Token>) arg;
//
//            NodeList<Modifier> modifiers = fieldDeclaration.getModifiers();
//            for (Modifier m : modifiers) {
//                System.out.println("Modifier = " + m.toString());
//            }
//
//            NodeList<VariableDeclarator> variables = fieldDeclaration.getVariables();
//            for (VariableDeclarator var : variables) {
//                System.out.println("Var = " + var.toString() + ", type = " + var.getType());
//            }
//        }
//    }

}

