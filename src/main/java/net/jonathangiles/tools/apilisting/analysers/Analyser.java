package net.jonathangiles.tools.apilisting.analysers;

import net.jonathangiles.tools.apilisting.model.APIListing;

import java.io.File;

public interface Analyser {

    void analyse(File inputFile, APIListing apiListing);
}
