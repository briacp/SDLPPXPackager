/**************************************************************************
 Copyright (C) 2020 Briac Pilpré

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/
package net.briac.sdlppx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Convert a Trados SDLPPX Project Package to SDLRPX Return Package.
 *
 * Original idea from Fi2Pro video : https://www.youtube.com/watch?v=a4ZGeAjTl2M
 *
 * @author briac
 *
 */
public class SDLPPXPackager {

    private static final String TARGET_DIR = "target";
    private static final String SOURCE_DIR = "source";
    private static final String TM_DIR = "tm";
    private static final String GLOSSARY_DIR = "glossary";
    private static final String HELP_LINE = "SDLPPXPackager [options] --project-dir project_dir [sdlppx|sdltm|sdltb]";

    private static final String EXT_SDLPROJ = ".sdlproj";
    private static final String EXT_SDLTM = ".sdltm";
    private static final String EXT_SDLTB = ".sdltb";
    private static final String EXT_SDLXLIFF = ".sdlxliff";

    private final Path sdlPpx;
    private String targetLanguage;
    private Document sdlProjDoc;
    private boolean noGlossary = false;
    private boolean noTMX = false;
    private boolean noSource = false;

    private static final String ATTRIBUTE_PACKAGE_TYPE = "PackageType";
    private static final int MAX_DEPTH = 10;

    private final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
    }

    private static final Logger LOGGER = Logger.getLogger(SDLPPXPackager.class.getName());

    public static enum PackageTypes {
        ProjectPackage, ReturnPackage
    }

    public SDLPPXPackager(String sdlPpx) throws FileNotFoundException {
        this.sdlPpx = Paths.get(sdlPpx);
        if (!this.sdlPpx.toFile().exists()) {
            LOGGER.log(Level.SEVERE, "SDLPPX file {0} does not exist.", new Object[] { sdlPpx });
            throw new FileNotFoundException("SDLPPX file does not exist.");
        }
    }

    public static void main(String[] args) {
        // CLI mode
        Options options = new Options();
        options.addOption("h", "help", false, "print this message");
        options.addOption("G", "gui", false, "force the GUI mode");
        options.addOption("p", "project-dir", true, "project directory");
        options.addOption("e", "extract", false, "extract source files from the SDLPPX");
        options.addOption("r", "return", false, "create the return SDLPRX package (default)");
        options.addOption("ng", "no-glossary", false, "skip the SDLTB glossary extraction");
        options.addOption("nt", "no-tm", false, "skip the SDLTM memory extraction");
        options.addOption("ns", "no-source", false, "skip the SDLXLIFF sources extraction");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            LOGGER.log(Level.SEVERE, "Error parsing the command line", e);
            System.exit(4);
        }

        String projectDir = cmd.getOptionValue("p");
        if (args.length == 0 || cmd.hasOption("G")) {
            SDLPPXPackagerWindow win = new SDLPPXPackagerWindow();

            if (cmd.hasOption("p")) {
                win.targetDir.setText(projectDir);
            }
            if (!cmd.getArgList().isEmpty()) {
                win.sdlppxFile.setText(cmd.getArgList().get(0));
            }
            if (cmd.hasOption("ng")) {
                win.cbSkipGlossary.setSelected(true);
            }
            if (cmd.hasOption("nt")) {
                win.cbSkipTM.setSelected(true);
            }
            if (cmd.hasOption("ns")) {
                win.cbSkipSources.setSelected(true);
            }

            win.setVisible(true);
            return;
        }

        HelpFormatter formatter = new HelpFormatter();
        if (cmd.hasOption("h")) {
            formatter.printHelp(HELP_LINE, options);
            System.exit(2);
        }

        if (cmd.getArgList().isEmpty()) {
            System.err.println("Missing required 'sdlppx' file parameter");
            formatter.printHelp(HELP_LINE, options);
            System.exit(3);
        }
        if (!cmd.hasOption("p")) {
            System.err.println("Missing required 'project-dir' parameter.");
            formatter.printHelp(HELP_LINE, options);
            System.exit(4);
        }

        File f = new File(cmd.getArgList().get(0));

        SDLPPXPackager sdl = null;
        try {
            sdl = new SDLPPXPackager(f.getAbsolutePath());
        } catch (FileNotFoundException e) {
            System.exit(5);
        }

        if (f.getName().toLowerCase().endsWith(".sdltb")) {
            String glossaryPrefix = f.getName().replaceFirst("\\.\\w+$", "");
            try {
                new SDLTBConverter().convertSDLTB(f, new File(projectDir, GLOSSARY_DIR), glossaryPrefix);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error converting the SLTB file", e);
                System.exit(5);
            }
        } else if (f.getName().toLowerCase().endsWith(".sdltm")) {
            try {
                new SDLTMConverter().convertSDLTM(f, new File(projectDir, TM_DIR));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error converting the SLTM file", e);
                System.exit(5);
            }
        } else {
            if (cmd.hasOption("ng")) {
                sdl.noGlossary = true;
            }
            if (cmd.hasOption("nt")) {
                sdl.noTMX = true;
            }
            if (cmd.hasOption("ns")) {
                sdl.noSource = true;
            }

            if (cmd.hasOption("extract")) {
                try {
                    sdl.extractFiles(projectDir);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error extracting source files", e);
                    System.exit(6);
                }
            } else {
                try {
                    sdl.updateSdlppx(projectDir);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error updating package", e);
                    System.exit(7);
                }
            }
        }

    }

    public boolean extractFiles(String projectDir) throws Exception {
        boolean allOk = true;

        if (!noSource) {
            try {
                extractSources(projectDir);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error extracting SDLTM", e);
                allOk = false;
            }
        }

        if (!noTMX) {
            try {
                extractTM(projectDir);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error extracting SDLTM", e);
                allOk = false;
            }
        }

        if (!noGlossary) {
            try {
                extractGlossaries(projectDir);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error extracting SDLTB", e);
                allOk = false;
            }
        }

        return allOk;
    }

    private void extractSources(String projectDir)
            throws IOException, ParserConfigurationException, SAXException, TransformerConfigurationException,
            TransformerFactoryConfigurationError, TransformerException {

        try (FileSystem zipfs = FileSystems.newFileSystem(sdlPpx, null)) {
            Stream<Path> sdlProjStream = Files.find(zipfs.getRootDirectories().iterator().next(), MAX_DEPTH,
                    (path, basicFileAttributes) -> path.toString().toLowerCase().endsWith(EXT_SDLPROJ));
            Optional<Path> projPath = sdlProjStream.findFirst();

            if (!projPath.isPresent()) {
                LOGGER.log(Level.WARNING, "Cannot find .sdlproj file inside the .sdlppx");
                sdlProjStream.close();
                return;
            }

            Path sdlProj = projPath.get();
            sdlProjStream.close();

            LOGGER.log(Level.INFO, "SDLProj file: {0}", sdlProj);
            parseSDLProj(sdlProj, false);
            File sourceDir = new File(projectDir, SOURCE_DIR);
            sourceDir.mkdirs();

            // We assume the target directories are always flat with sdlxliff?
            Files.find(zipfs.getPath(targetLanguage), MAX_DEPTH,
                    (path, basicFileAttributes) -> path.toString().toLowerCase().endsWith(EXT_SDLXLIFF))
                    .forEach(actionPath -> {
                        Path source = Paths.get(sourceDir.getAbsolutePath(),
                                actionPath.getFileName().toString());
                        LOGGER.log(Level.INFO, "Copy source file {0} to {1}",
                                new Object[] { actionPath, source });
                        try {
                            Files.copy(actionPath, source, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    private void extractGlossaries(String projectDir) throws Exception {

        try (FileSystem zipfs = FileSystems.newFileSystem(sdlPpx, null)) {
            List<Path> sdltmFiles = Files
                    .find(zipfs.getRootDirectories().iterator().next(), MAX_DEPTH,
                            (path, basicFileAttributes) -> path.toString().toLowerCase().endsWith(EXT_SDLTB))
                    .collect(Collectors.toList());
            LOGGER.log(Level.INFO, "SDLTB file: {0} found", sdltmFiles.size());
            for (Path sdltm : sdltmFiles) {
                File tmpFile = File.createTempFile("sdlppx_", ".sdltb");
                FileOutputStream fos = new FileOutputStream(tmpFile);
                Files.copy(sdltm, fos);
                fos.close();
                String glossaryPrefix = sdlPpx.getFileName().toString().replaceFirst("\\.\\w+$", "");
                new SDLTBConverter().convertSDLTB(tmpFile, new File(projectDir, GLOSSARY_DIR),
                        glossaryPrefix);
                tmpFile.delete();
            }
        }

    }

    private void extractTM(String projectDir) throws Exception {
        try (FileSystem zipfs = FileSystems.newFileSystem(sdlPpx, null)) {
            List<Path> sdltmFiles = Files
                    .find(zipfs.getRootDirectories().iterator().next(), 10,
                            (path, basicFileAttributes) -> path.toString().toLowerCase().endsWith(EXT_SDLTM))
                    .collect(Collectors.toList());
            LOGGER.log(Level.INFO, "SDLTM file: {0} found", sdltmFiles.size());
            for (Path sdltm : sdltmFiles) {
                File tmpFile = File.createTempFile("sdlppx_", ".sdltm");
                FileOutputStream fos = new FileOutputStream(tmpFile);
                Files.copy(sdltm, fos);
                fos.close();
                new SDLTMConverter().convertSDLTM(tmpFile, new File(projectDir, TM_DIR));
                tmpFile.delete();
            }
        }
    }

    public boolean updateSdlppx(String projectDir) throws Exception {

        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        Files.copy(sdlPpx, sdlPpx.resolveSibling(sdlPpx.getFileName().toString() + ".bak"),
                StandardCopyOption.REPLACE_EXISTING);

        boolean isUpdated = false;
        try (FileSystem zipfs = FileSystems.newFileSystem(sdlPpx, null)) {
            Stream<Path> sdlProjStream = Files.find(zipfs.getRootDirectories().iterator().next(), 1,
                    (path, basicFileAttributes) -> path.toString().toLowerCase().endsWith(EXT_SDLPROJ));
            Optional<Path> projPath = sdlProjStream.findFirst();

            if (!projPath.isPresent()) {
                LOGGER.log(Level.WARNING, "Cannot find .sdlproj file inside the .sdlppx");
                sdlProjStream.close();
                return false;
            }

            Path sdlProj = projPath.get();
            sdlProjStream.close();

            LOGGER.log(Level.INFO, "SDLProj file: {0}", sdlProj);
            isUpdated = parseSDLProj(sdlProj, true);

            // We assume the target directories are always flat with sdlxliff?
            Files.find(zipfs.getPath(targetLanguage), 1,
                    (path, basicFileAttributes) -> path.getFileName().toString().endsWith(".sdlxliff"))
                    .forEach(actionPath -> {
                        Path source = Paths.get(projectDir, TARGET_DIR, actionPath.getFileName().toString());
                        try {
                            Files.copy(source, actionPath, StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.log(Level.INFO, "Replace {0} > {1}", new Object[] { source, actionPath });
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Could not replace {0} > {1}",
                                    new Object[] { source, actionPath });
                            LOGGER.log(Level.WARNING, "", e);
                        }
                    });
        }
        if (isUpdated) {
            Path sdlRpx = sdlPpx
                    .resolveSibling(sdlPpx.getFileName().toString().replaceAll("\\.sdlppx$", ".sdlrpx"));
            LOGGER.log(Level.INFO, "Renaming {0} to {1}", new Object[] { sdlPpx, sdlRpx });
            Files.move(sdlPpx, sdlRpx, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }

        return false;
    }

    private boolean parseSDLProj(final Path sdlProj, final boolean doUpdate)
            throws ParserConfigurationException, SAXException, TransformerConfigurationException,
            TransformerFactoryConfigurationError, IOException, TransformerException {

        final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        // Important, the DocumentBuilder must be created with an InputStream,
        // otherwise
        // the zip can't be updated as the stream is not explicitely closed.
        try (InputStream is = Files.newInputStream(sdlProj, StandardOpenOption.READ)) {
            sdlProjDoc = docBuilder.parse(is);
        } catch (IOException e) {
            return false;
        }

        // Language code, obviously we don't deal with multiple target
        // languages.
        // /PackageProject/LanguageDirections/LanguageDirection/@TargetLanguageCode="fr-FR"
        targetLanguage = ((Element) ((Element) ((Element) sdlProjDoc.getElementsByTagName("PackageProject")
                .item(0)).getElementsByTagName("LanguageDirections").item(0))
                        .getElementsByTagName("LanguageDirection").item(0))
                                .getAttribute("TargetLanguageCode");

        LOGGER.log(Level.INFO, "Target Language: {0}", targetLanguage);

        if (!doUpdate) {
            return false;
        }

        final Node attrPackageType = sdlProjDoc.getDocumentElement().getAttributeNode(ATTRIBUTE_PACKAGE_TYPE);
        final PackageTypes packageType = PackageTypes.valueOf(attrPackageType.getTextContent());
        switch (packageType) {
        case ProjectPackage:
            LOGGER.info("This is a project package. Changing to ReturnPackage");
            attrPackageType.setNodeValue(PackageTypes.ReturnPackage.toString());
            updateDoc(sdlProj, sdlProjDoc);
            return true;

        case ReturnPackage:
            LOGGER.info("This is a return package. Nothing to do.");
            break;
        }

        return false;
    }

    private void updateDoc(final Path sdlProj, final Document doc)
            throws TransformerFactoryConfigurationError, TransformerConfigurationException, IOException,
            TransformerException {
        // write the content into xml file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        Path tmpSdlProj = Files.createTempFile("sdlproj_", ".tmp");

        // Make sure we delete the temp file at shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    Files.delete(tmpSdlProj);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error deleting temporary sdlproj file", e);
                }
            }
        });

        // https://stackoverflow.com/questions/32353423/can-a-jar-file-be-updated-programmatically-without-rewriting-the-whole-file#32944829
        try (FileChannel channel = FileChannel.open(tmpSdlProj, StandardOpenOption.WRITE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                OutputStream cos = Channels.newOutputStream(channel)) {
            StreamResult result = new StreamResult(cos);
            transformer.transform(new DOMSource(doc), result);

            cos.close();
        }

        Files.copy(tmpSdlProj, sdlProj, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean isNoTMX() {
        return noTMX;
    }

    public void setNoTMX(boolean noTMX) {
        this.noTMX = noTMX;
    }

    public boolean isNoGlossary() {
        return noGlossary;
    }

    public void setNoGlossary(boolean noGlossary) {
        this.noGlossary = noGlossary;
    }

    public boolean isNoSource() {
        return noSource;
    }

    public void setNoSource(boolean noSource) {
        this.noSource = noSource;
    }

}
