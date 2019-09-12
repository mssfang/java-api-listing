package net.jonathangiles.tools.apilisting;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jonathangiles.tools.apilisting.analysers.ASTAnalyser;
import net.jonathangiles.tools.apilisting.analysers.Analyser;
import net.jonathangiles.tools.apilisting.analysers.ReflectiveAnalyser;
import net.jonathangiles.tools.apilisting.model.APIListing;
import net.jonathangiles.tools.apilisting.model.Token;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.fasterxml.jackson.databind.MapperFeature.*;
import static net.jonathangiles.tools.apilisting.model.TokenKind.*;

public class Main {
//
//    // expected argument order:
//    // <reviewName> <jarFile> <outputFile>
//    public static void main(String[] args) {
//        // TODO validate input
//        if (args.length != 3) {
//            System.out.println("Expected argument order: <reviewName> <jarFile> <outputFile>, e.g. \"Storage Review\" /path/to/jarfile.jar report.json");
//            System.exit(-1);
//        }
//
//        final String reviewName = args[0];
//        final String jarFile = args[1];
//        final String outputFile = args[2];
//
//        new Main(reviewName, jarFile, outputFile);
//    }
//
//    public Main(String reviewName, String inputFile, String outputFile) {
//        final File file = new File(inputFile);
//
//        if (!file.exists()) {
//            System.err.println("Cannot load find file '" + inputFile + "'");
//            System.exit(-1);
//        }
//
//        APIListing apiListing = new APIListing();
//        apiListing.setName(reviewName);
//
//        // empty tokens list that we will fill as we process each class file
//        List<Token> tokens = new ArrayList<>();
//        apiListing.setTokens(tokens);
//
//        Analyser analyser = new ReflectiveAnalyser();
//
//        final File tempDir = new File("temp/" + file.getName());
//        try {
//            // delete any existing archive output and re-extract jar file
//            deleteDirectory(tempDir.toPath());
//            extractArchive(file.toPath(), tempDir.toPath());
//
//            // do analysis on every file - it is up to the analyser to decide to accept or reject the file
//            try (Stream<Path> stream = Files.walk(tempDir.toPath(), Integer.MAX_VALUE)) {
//                stream.forEach(path -> analyser.analyse(path.toFile(), apiListing));
//            }
//
//            // delete temporary directory contents
//            deleteDirectory(tempDir.toPath());
//        } catch (IOException e) {
//            e.printStackTrace();
//            System.exit(-1);
//        }
//
//        // print to console
//        tokens.stream().forEach(token -> {
//            if (token.getKind() == NEW_LINE) {
//                System.out.println("");
//            } else {
//                System.out.print(token.getValue());
//            }
//        });
//
//        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            objectMapper.disable(
//                    AUTO_DETECT_CREATORS,
//                    AUTO_DETECT_FIELDS,
//                    AUTO_DETECT_GETTERS,
//                    AUTO_DETECT_IS_GETTERS);
//            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFile), apiListing);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void extractArchive(Path archiveFile, Path destPath) throws IOException {
//        Files.createDirectories(destPath); // create dest path folder(s)
//
//        try (ZipFile archive = new ZipFile(archiveFile.toFile())) {
//            // sort entries by name to always create folders first
//            List<? extends ZipEntry> entries = archive.stream()
//                    .sorted(Comparator.comparing(ZipEntry::getName))
//                    .collect(Collectors.toList());
//
//            // copy each entry in the dest path
//            for (ZipEntry entry : entries) {
//                Path entryDest = destPath.resolve(entry.getName());
//
//                if (entry.isDirectory()) {
//                    Files.createDirectory(entryDest);
//                    continue;
//                }
//
//                Files.copy(archive.getInputStream(entry), entryDest);
//            }
//        }
//    }
//
//    private void deleteDirectory(Path path) throws IOException {
//        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
//            @Override
//            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
//                Files.delete(dir);
//                return FileVisitResult.CONTINUE;
//            }
//
//            @Override
//            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//                Files.delete(file);
//                return FileVisitResult.CONTINUE;
//            }
//        });
//    }



// Used old version of main method to test for single file


    public static void main(String[] args) {
        // TODO validate input

        String reviewName = "Storage GA review";
        String outputFile = "target/result.json";
        String jarFile = "src/main/resources/net/jonathangiles/tools/apilisting/tests/BlobAsyncClient.java";

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