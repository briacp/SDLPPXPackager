/**************************************************************************
 Copyright (C) 2019 Briac Pilpré

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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private static final Logger LOGGER = Logger.getLogger(SDLPPXPackager.class.getName());

    private static final String ATTRIBUTE_PACKAGE_TYPE = "PackageType";

    public static enum PackageTypes {
        ProjectPackage, ReturnPackage
    }

    private final Path sdlPpx;
    private final Path targetDir;
    private String targetLanguage;

    public SDLPPXPackager(String filename, String targetDir) throws FileNotFoundException {
        this.sdlPpx = Paths.get(filename);
        this.targetDir = Paths.get(targetDir);
        if (!sdlPpx.toFile().exists()) {
            throw new FileNotFoundException("SDLPPX file does not exist.");
        }
        if (!this.targetDir.toFile().exists()) {
            throw new FileNotFoundException("Target directory does not exist.");
        }

    }

    public static void main(String[] args) throws Throwable {
        if (args.length < 2) {
            SDLPPXPackagerWindow win = new SDLPPXPackagerWindow();
            win.setVisible(true);
        } else {
            new SDLPPXPackager(args[0], args[1]).updateSdlppx();
        }
    }

    public boolean updateSdlppx() throws IOException, TransformerConfigurationException, ParserConfigurationException,
            SAXException, TransformerFactoryConfigurationError, TransformerException {

        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        Files.copy(sdlPpx, sdlPpx.resolveSibling(sdlPpx.getFileName().toString() + ".bak"),
                StandardCopyOption.REPLACE_EXISTING);

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.sdlproj");
        boolean isUpdated = false;
        try (FileSystem zipfs = FileSystems.newFileSystem(sdlPpx, null)) {
            Stream<Path> sdlProjStream = Files.find(zipfs.getRootDirectories().iterator().next(), 1,
                    (path, basicFileAttributes) -> matcher.matches(path));
            Path sdlProj = sdlProjStream.findFirst().get();
            sdlProjStream.close();

            LOGGER.log(Level.INFO, "SDLProj file: {0}", sdlProj);
            isUpdated = parseSDLProj(sdlProj);
            
            // We assume the target directories are always flat with sdlxliff?
            Files.find(zipfs.getPath(targetLanguage), 1, (path, basicFileAttributes) -> path.getFileName().toString().endsWith(".sdlxliff"))
                 .forEach(actionPath -> {
                     Path source = Paths.get(targetDir.toFile().getAbsolutePath(), actionPath.getFileName().toString());
                     LOGGER.log(Level.INFO, "Replace {0} > {1}", new Object[] {source, actionPath });
                     try {
                        Files.copy(source, actionPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                 });
        }
        if (isUpdated) {
            Path sdlRpx = sdlPpx.resolveSibling(sdlPpx.getFileName().toString().replaceAll("\\.sdlppx$", ".sdlrpx"));
            LOGGER.log(Level.INFO, "Renaming {0} to {1}", new Object[] { sdlPpx, sdlRpx });
            Files.move(sdlPpx, sdlRpx, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }

        return false;
    }

    private boolean parseSDLProj(final Path sdlProj) throws ParserConfigurationException, SAXException,
            TransformerConfigurationException, TransformerFactoryConfigurationError, IOException, TransformerException {

        final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        // Important, the DocumentBuilder must be created with an InputStream, otherwise
        // the zip can't be updated as the stream is not explicitely closed.
        Document doc;
        try (InputStream is = Files.newInputStream(sdlProj, StandardOpenOption.READ)) {
            doc = docBuilder.parse(is);
        } catch (IOException e) {
            return false;
        }

        // Language code, obviously we don't deal with multiple target languages.
        // /PackageProject/LanguageDirections/LanguageDirection/@TargetLanguageCode="fr-FR"
        targetLanguage = ((Element) ((Element) ((Element) doc
                .getElementsByTagName("PackageProject").item(0))
                .getElementsByTagName("LanguageDirections").item(0))
                .getElementsByTagName("LanguageDirection").item(0)).getAttribute("TargetLanguageCode");
        
        LOGGER.log(Level.INFO, "Target Language: {0}", targetLanguage);
        
        final Node attrPackageType = doc.getDocumentElement().getAttributeNode(ATTRIBUTE_PACKAGE_TYPE);
        final PackageTypes packageType = PackageTypes.valueOf(attrPackageType.getTextContent());
        switch (packageType) {
        case ProjectPackage:
            LOGGER.info("This is a project package. Changing to ReturnPackage");
            attrPackageType.setNodeValue(PackageTypes.ReturnPackage.toString());
            updateDoc(sdlProj, doc);
            return true;

        case ReturnPackage:
            LOGGER.info("This is a return package. Nothing to do.");
            break;
        }

        return false;

    }

    private void updateDoc(final Path sdlProj, final Document doc) throws TransformerFactoryConfigurationError,
            TransformerConfigurationException, IOException, TransformerException {
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
        try (FileChannel channel = FileChannel.open(tmpSdlProj, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING); OutputStream cos = Channels.newOutputStream(channel)) {
            StreamResult result = new StreamResult(cos);
            transformer.transform(new DOMSource(doc), result);

            cos.close();
        }

        Files.copy(tmpSdlProj, sdlProj, StandardCopyOption.REPLACE_EXISTING);
    }

}
