package net.jonathangiles.tools.apilisting.analysers;

import net.jonathangiles.tools.apilisting.model.APIListing;
import net.jonathangiles.tools.apilisting.model.ChildItem;
import net.jonathangiles.tools.apilisting.model.Token;
import net.jonathangiles.tools.apilisting.model.TypeKind;
import net.jonathangiles.tools.apilisting.tests.Test1;
import net.jonathangiles.tools.apilisting.tests.Test2;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static net.jonathangiles.tools.apilisting.model.TokenKind.KEYWORD;
import static net.jonathangiles.tools.apilisting.model.TokenKind.MEMBER_NAME;
import static net.jonathangiles.tools.apilisting.model.TokenKind.NEW_LINE;
import static net.jonathangiles.tools.apilisting.model.TokenKind.PUNCTUATION;
import static net.jonathangiles.tools.apilisting.model.TokenKind.TEXT;
import static net.jonathangiles.tools.apilisting.model.TokenKind.TYPE_NAME;
import static net.jonathangiles.tools.apilisting.model.TokenKind.WHITESPACE;

public class ReflectiveAnalyser implements Analyser {

    public void analyse(File inputFile, APIListing apiListing) {
        // Root Navigation
        ChildItem rootNavForJar = new ChildItem(inputFile.getName());
        apiListing.addChildItem(rootNavForJar);

        // TODO get all class files from the jar file and process them individually
        getClassAPI(Test1.class, apiListing, rootNavForJar);
        getClassAPI(Test2.class, apiListing, rootNavForJar);
    }

    private void getClassAPI(Class<?> cls, APIListing apiListing, ChildItem parent) {
        final List<Token> tokens = apiListing.getTokens();

        // class modifier
        boolean isPublicClass = getModifiers(cls.getModifiers(), tokens);
        if (!isPublicClass) {
            return;
        }

        // Create navigation for this class and add it to the parent
        ChildItem classNav = new ChildItem(cls.getSimpleName(), cls.getSimpleName(), TypeKind.fromClass(cls));
        parent.addChildItem(classNav);

        // class name
        tokens.add(new Token(KEYWORD, "class"));
        tokens.add(new Token(WHITESPACE, " "));
        tokens.add(new Token(TYPE_NAME, cls.getSimpleName()));
        tokens.add(new Token(WHITESPACE, " "));
        tokens.add(new Token(PUNCTUATION, "{"));
        tokens.add(new Token(NEW_LINE, ""));

        // fields
        Stream.of(cls.getDeclaredFields())
                .sorted(Comparator.comparing(Field::getName))
                .forEach(field ->  {
                    // modifiers
                    boolean isPublicAPI = getModifiers(field.getModifiers(), tokens);
                    if (!isPublicAPI) {
                        return;
                    }

                    // field type
                    getType(field.getGenericType(), tokens);
                    tokens.add(new Token(WHITESPACE, " "));

                    // field name
                    tokens.add(new Token(MEMBER_NAME, field.getName()));

                    tokens.add(new Token(PUNCTUATION, ";"));
                    tokens.add(new Token(NEW_LINE, ""));
                });

        // constructors
        Stream.of(cls.getDeclaredConstructors())
                .sorted(Comparator.comparing(Constructor::getName))
                .forEach(constructor ->  {
                    // modifiers
                    boolean isPublicAPI = getModifiers(constructor.getModifiers(), tokens);
                    if (!isPublicAPI) {
                        return;
                    }

                    // constructor name
                    tokens.add(new Token(MEMBER_NAME, constructor.getDeclaringClass().getSimpleName()));

                    // opening brace
                    tokens.add(new Token(PUNCTUATION, "("));

                    // parameters
                    getParameters(constructor.getParameters(), tokens);

                    // closing brace and new line
                    tokens.add(new Token(PUNCTUATION, ")"));
                    tokens.add(new Token(WHITESPACE, " "));
                    tokens.add(new Token(PUNCTUATION, "{"));
                    tokens.add(new Token(WHITESPACE, " "));
                    tokens.add(new Token(PUNCTUATION, "}"));
                    tokens.add(new Token(NEW_LINE, ""));
                });

        // methods
        Stream.of(cls.getDeclaredMethods())
                .sorted(Comparator.comparing(Method::getName))
                .forEach(method -> {
                    // modifiers
                    boolean isPublicAPI = getModifiers(method.getModifiers(), tokens);
                    if (!isPublicAPI) {
                        return;
                    }

                    // return type
                    getType(method.getGenericReturnType(), tokens);
                    tokens.add(new Token(WHITESPACE, " "));

                    // method name
                    tokens.add(new Token(MEMBER_NAME, method.getName()));

                    // opening brace
                    tokens.add(new Token(PUNCTUATION, "("));

                    // parameters
                    getParameters(method.getParameters(), tokens);

                    // closing brace and new line
                    tokens.add(new Token(PUNCTUATION, ")"));
                    tokens.add(new Token(WHITESPACE, " "));
                    tokens.add(new Token(PUNCTUATION, "{"));
                    tokens.add(new Token(WHITESPACE, " "));
                    tokens.add(new Token(PUNCTUATION, "}"));
                    tokens.add(new Token(NEW_LINE, ""));
                });

        // TODO handle enclosed classes, passing in child navigation as we go deeper
        Stream.of(cls.getClasses())
                .forEach(subclass -> {
                    getClassAPI(subclass, apiListing, classNav);
                });

        // close class
        tokens.add(new Token(PUNCTUATION, "}"));
        tokens.add(new Token(NEW_LINE, ""));
    }

    private boolean getModifiers(int modifiers, List<Token> tokens) {
        if (isPublic(modifiers)) {
            tokens.add(new Token(KEYWORD, "public"));
        } else if (isProtected(modifiers)) {
            tokens.add(new Token(KEYWORD, "protected"));
        } else {
            // abort - we only care about public and protected methods
            return false;
        }

        tokens.add(new Token(WHITESPACE, " "));

        if (isAbstract(modifiers)) {
            tokens.add(new Token(KEYWORD, "abstract"));
            tokens.add(new Token(WHITESPACE, " "));
        }
        if (isFinal(modifiers)) {
            tokens.add(new Token(KEYWORD, "final"));
            tokens.add(new Token(WHITESPACE, " "));
        }
        if (isStatic(modifiers)) {
            tokens.add(new Token(KEYWORD, "static"));
            tokens.add(new Token(WHITESPACE, " "));
        }

        return true;
    }

    private void getParameters(Parameter[] parameters, List<Token> tokens) {
        for(int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            getType(parameter.getParameterizedType(), tokens);
            tokens.add(new Token(WHITESPACE, " "));
            tokens.add(new Token(TEXT, parameter.getName()));

            // add comma and space until the last parameter
            if (i < parameters.length - 1) {
                tokens.add(new Token(PUNCTUATION, ","));
                tokens.add(new Token(WHITESPACE, " "));
            }
        }
    }

    private void getType(Type type, List<Token> tokens) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] parameterTypes = parameterizedType.getActualTypeArguments();

            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            getType(rawType, tokens);
            tokens.add(new Token(PUNCTUATION, "<"));

            for(int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i] instanceof Class) {
                    tokens.add(new Token(TYPE_NAME, ((Class<?>) parameterTypes[i]).getSimpleName()));
                } else if (parameterTypes[i] instanceof TypeVariable) {
                    tokens.add(new Token(TYPE_NAME, ((TypeVariable<?>) parameterTypes[i]).getName()));
                } else {
                    System.err.println("Unknown type " + parameterTypes[i] + " of type " + parameterTypes[i].getClass());
                }

                // add comma and space until the last parameter
                if (i < parameterTypes.length - 1) {
                    tokens.add(new Token(PUNCTUATION, ","));
                    tokens.add(new Token(WHITESPACE, " "));
                }
            }

            tokens.add(new Token(PUNCTUATION, ">"));
        } else if (type instanceof Class) {
            tokens.add(new Token(TYPE_NAME, getType((Class<?>)type)));
        } else if (type instanceof TypeVariable) {
            tokens.add(new Token(TYPE_NAME, ((TypeVariable<?>) type).getName()));
        } else {
            System.err.println("Unknown type " + type + " of type " + type.getClass());
        }
    }

    private String getType(Class<?> type) {
        if (type.isArray()) {
            return getType(type.getComponentType()) + "[]";
        } else {
            return type.getSimpleName();
        }
    }
}
