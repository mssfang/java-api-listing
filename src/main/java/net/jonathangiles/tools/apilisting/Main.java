package net.jonathangiles.tools.apilisting;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jonathangiles.tools.apilisting.analysers.ASTAnalyser;
import net.jonathangiles.tools.apilisting.analysers.ReflectiveAnalyser;
import net.jonathangiles.tools.apilisting.model.APIListing;
import net.jonathangiles.tools.apilisting.model.Token;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.databind.MapperFeature.*;
import static net.jonathangiles.tools.apilisting.model.TokenKind.*;

public class Main {

    public static void main(String[] args) {
        // TODO validate input

        String reviewName = "Storage GA review";
//        String jarFile = "target/classes/net/jonathangiles/tools/apilisting/tests/Test1.class";
        String jarFile = "src/main/java/net/jonathangiles/tools/apilisting/tests/Test1.java";
//        String jarFile = "src/main/java/net/jonathangiles/tools/apilisting/analysers/Test11.java";

        String outputFile = "target/result.json";
        Main main = new Main(reviewName, jarFile, outputFile);
    }

    public Main(String reviewName, String inputFile, String outputFile) {
        final File file = new File(inputFile);

        if (!file.exists()) {
            System.err.println("Cannot load find file '" + inputFile + "'");
            System.exit(-1);
        }

        APIListing apiListing = new APIListing();
        apiListing.setName(reviewName);

        // empty tokens list that we will fill as we process each class file
        List<Token> tokens = new ArrayList<>();
        apiListing.setTokens(tokens);

        // TODO select analyser based on user input
//        new ReflectiveAnalyser().analyse(file, apiListing);
        new ASTAnalyser().analyse(file, apiListing);

        // print to console
        tokens.stream().forEach(token -> {
            if (token.getKind() == NEW_LINE) {
                System.out.println("");
            } else {
                System.out.print(token.getValue());
            }
        });

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.disable(
                    AUTO_DETECT_CREATORS,
                    AUTO_DETECT_FIELDS,
                    AUTO_DETECT_GETTERS,
                    AUTO_DETECT_IS_GETTERS);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFile), apiListing);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
