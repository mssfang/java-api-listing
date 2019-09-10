package net.jonathangiles.tools.apilisting.analysers;

import net.jonathangiles.tools.apilisting.model.APIListing;
import net.jonathangiles.tools.apilisting.model.ChildItem;

import java.io.File;

public class ASTAnalyser implements Analyser {

    @Override
    public void analyse(File inputFile, APIListing apiListing) {
        // Root Navigation
        ChildItem rootNavForJar = new ChildItem(inputFile.getName());
        apiListing.addChildItem(rootNavForJar);

        // TODO get all class files from the jar file and process them individually

    }
}

