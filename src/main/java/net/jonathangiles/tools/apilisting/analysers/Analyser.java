package net.jonathangiles.tools.apilisting.analysers;

import net.jonathangiles.tools.apilisting.model.APIListing;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public interface Analyser {

    void analyse(List<Path> allFiles, File tempDir, APIListing apiListing);
}
