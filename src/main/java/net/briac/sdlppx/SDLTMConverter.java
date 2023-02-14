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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SDLTMConverter {
    private static final Logger LOGGER = Logger.getLogger(SDLTMConverter.class.getName());

    private final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();

    public void convertSDLTM(File sdltmFile, File outputDir) throws Exception {
        Connection connection = null;

        final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document tmxDoc = docBuilder.newDocument();

        Package pack = this.getClass().getPackage();
        Element tmxEl = tmxDoc.createElement("tmx");
        tmxEl.setAttribute("version", "1.1");
        tmxDoc.appendChild(tmxEl);
        Element headerEl = tmxDoc.createElement("header");
        headerEl.setAttribute("creationtool", pack.getImplementationTitle());
        headerEl.setAttribute("o-tmf", "SDLTM");
        headerEl.setAttribute("adminlang", "en-US");
        headerEl.setAttribute("datatype", "plaintext");
        headerEl.setAttribute("creationtoolversion", pack.getImplementationVersion());
        headerEl.setAttribute("segtype", "sentence");
        tmxEl.appendChild(headerEl);

        Element bodyEl = tmxDoc.createElement("body");
        tmxEl.appendChild(bodyEl);
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + sdltmFile.toURI());

            Statement statement = connection.createStatement();
            statement.closeOnCompletion();
            ResultSet rs = statement
                    .executeQuery("select name, source_language, tucount from translation_memories");
            String tmName = rs.getString(1);
            String srcLang = rs.getString(2);
            Integer sdltmCount = rs.getInt(3);
            headerEl.setAttribute("srclang", srcLang);
            if (rs.next()) {
                LOGGER.log(Level.WARNING,
                        "Multiple source languages in SDLTM, only the first one is used ({0})", srcLang);
            }
            rs.close();

            statement = connection.createStatement();
            rs = statement.executeQuery("select id, source_segment, target_segment from translation_units");

            int tmxCount = 0;
            while (rs.next()) {
                Element tu = tmxDoc.createElement("tu");
                Document sourceXML = docBuilder.parse(new ByteArrayInputStream(
                        rs.getString("source_segment").getBytes(StandardCharsets.UTF_8)));
                Document targetXML = docBuilder.parse(new ByteArrayInputStream(
                        rs.getString("target_segment").getBytes(StandardCharsets.UTF_8)));
                tu.appendChild(createTuv(tmxDoc, sourceXML));
                tu.appendChild(createTuv(tmxDoc, targetXML));
                bodyEl.appendChild(tu);
                tmxCount++;
            }

            File tmFile = new File(outputDir, tmName + ".tmx");
            outputDir.mkdirs();
            LOGGER.log(Level.INFO, "Saving TMX file {1} TU ({2} in sdltm) to file {0}",
                    new Object[] { tmFile, tmxCount, sdltmCount });

            FileOutputStream fos = new FileOutputStream(tmFile);
            StreamResult result = new StreamResult(fos);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            // Don't indent as it messes up tags in segment (or use xml:space in
            // the tuv?)
            // transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT,
            // "yes");
            // transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
            // "2");
            transformer.transform(new DOMSource(tmxDoc), result);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error while extracting SDLTM");
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error while closing SDLTM");
            }
        }
    }

    private Element createTuv(Document tmxDoc, Document targetXML) {
        Element tuv = tmxDoc.createElement("tuv");
        Element seg = tmxDoc.createElement("seg");
        Element segment = (Element) targetXML.getElementsByTagName("Segment").item(0);
        tuv.setAttribute("lang", segment.getElementsByTagName("CultureName").item(0).getTextContent());

        NodeList nodes = segment.getElementsByTagName("Elements").item(0).getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element item = (Element) nodes.item(i);
            if (item.getTagName().equals("Tag")) {
                String tagType = item.getElementsByTagName("Type").item(0).getTextContent();
                String tagAnchor = item.getElementsByTagName("Anchor").item(0).getTextContent();
                String tagAlignmentAnchor = item.getElementsByTagName("AlignmentAnchor").item(0)
                        .getTextContent();
                Element tagEl;
                String tagId;
                switch (tagType) {
                case "Start":
                    tagEl = tmxDoc.createElement("bpt");
                    tagId = item.getElementsByTagName("TagID").item(0).getTextContent();
                    tagEl.setAttribute("type", tagId);
                    tagEl.setAttribute("i", tagAnchor);
                    tagEl.setAttribute("x", tagAlignmentAnchor);
                    break;
                case "End":
                    tagEl = tmxDoc.createElement("ept");
                    tagEl.setAttribute("i", tagAnchor);
                    break;
                default:
                    tagEl = tmxDoc.createElement("ph");
                    tagId = item.getElementsByTagName("TagID").item(0).getTextContent();
                    tagEl.setAttribute("type", tagId);
                    tagEl.setAttribute("x", tagAlignmentAnchor);
                    break;
                }
                seg.appendChild(tagEl);
            } else if (item.getTagName().equals("Text")) {
                seg.appendChild(
                        tmxDoc.createTextNode(item.getElementsByTagName("Value").item(0).getTextContent()));
            }
        }
        tuv.appendChild(seg);

        return tuv;
    }

}
