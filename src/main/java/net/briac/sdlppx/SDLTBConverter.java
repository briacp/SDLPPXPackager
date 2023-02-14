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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import net.briac.sdlppx.model.Concept;
import net.briac.sdlppx.model.Term;
import net.briac.sdlppx.model.TermBase;
import net.briac.sdlppx.model.TermGroup;

// Conversion from SLDTB to CSV 
// From https://github.com/TomasoAlbinoni/Trados-Studio-Resource-Converter/
public class SDLTBConverter {
    private static final Logger LOGGER = Logger.getLogger(SDLTBConverter.class.getName());

    public enum OutputType {
        COMMA_CSV(",", ".csv"), SEMICOLON_CSV(";", ".csv"), TAB_TXT("\t", ".txt"), OMEGAT("\t", ".txt");

        private String sep;
        private String ext;

        private OutputType(String sep, String ext) {
            this.sep = sep;
            this.ext = ext;
        }
    }

    public enum Synonym {
        COLUMN, PIPE
    }

    private static final String NEW_LINE = System.getProperty("line.separator");
    private OutputType outputType = OutputType.OMEGAT;
    private Synonym synonym = Synonym.COLUMN;

    public void convertSDLTB(File sdltbFile, File outputDir, String prefix) throws Exception {
        LOGGER.log(Level.INFO, "Converting {0} to {1}", new Object[] { sdltbFile, outputDir });

        outputDir.mkdirs();

        // Read SDLTB data into termbase object
        TermBase termbase = extractTermBase(sdltbFile);

        File outputFile = new File(outputDir,
                prefix + "_glossary_" + String.join("_", termbase.languages.keySet()) + outputType.ext);

        if (outputType == OutputType.OMEGAT) {
            writeOmegaT(outputFile, termbase);
        } else {
            writeCSV(outputFile, termbase);
        }

        LOGGER.log(Level.INFO, "SDLTB converted");
    }

    private void writeOmegaT(File outputFile, TermBase termbase) {
        BufferedWriter out;
        // Write csv
        try {
            out = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outputFile.toString()), "UTF-8"));

            // ========================== write rows =======================
            // for each concept
            for (Map.Entry<Integer, Concept> conceptEntry : termbase.concepts.entrySet()) {

                // Each language
                for (Map.Entry<String, Integer> languageEntry : termbase.languages.entrySet()) {
                    if (conceptEntry.getValue().termGroups.get(languageEntry.getKey()) != null) {
                        // Write all synonyms in one language
                        if (synonym == Synonym.COLUMN) {
                            for (Term storedterm : conceptEntry.getValue().termGroups
                                    .get(languageEntry.getKey()).terms) {
                                writeCSV(out, storedterm.getWord());
                            }
                            // // Fill up with empty cells
                            // for (int i = 0; i < languageEntry.getValue()
                            // -
                            // conceptEntry.getValue().termGroups.get(languageEntry.getKey()).terms
                            // .size(); i++) {
                            // out.write(outputType.sep + outputType.sep +
                            // outputType.sep);
                            // }
                        } else if (synonym == Synonym.PIPE) {
                            String termsWithPipes = "";
                            for (Term storedterm : conceptEntry.getValue().termGroups
                                    .get(languageEntry.getKey()).terms) {
                                if (storedterm.getTermInfo().equals("NonTerm")) {
                                    termsWithPipes += "(NOT: " + storedterm.getWord() + ")|";
                                } else {
                                    termsWithPipes = storedterm.getWord() + '|' + termsWithPipes;
                                }
                            }
                            // Remove the last pipe
                            termsWithPipes = termsWithPipes.substring(0, termsWithPipes.length() - 1);
                            writeCSV(out, termsWithPipes);
                            termsWithPipes = "";
                        }

                        writeCSV(out, conceptEntry.getValue().termGroups.get(languageEntry.getKey())
                                .getDefinition());
                    } else { // If no terms in given language, fill up with
                             // empty cells
                        out.write(outputType.sep); // For definition
                        if (synonym == Synonym.COLUMN) {
                            for (int i = 0; i < languageEntry.getValue(); i++) {
                                out.write(outputType.sep + outputType.sep + outputType.sep);
                            }
                        } else if (synonym == Synonym.PIPE) {
                            out.write(outputType.sep);
                        }
                    }
                }

                out.write("\n");
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getLocalizedMessage());
        }
    }

    private void writeCSV(File outputFile, TermBase termbase) {
        BufferedWriter out;
        // Write csv
        try {
            out = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outputFile.toString()), "UTF-8"));

            // =================== write first line ====================
            writeCSV(out, "Entry_Created");
            writeCSV(out, "Entry_Creator");
            writeCSV(out, "Entry_LastModified");
            writeCSV(out, "Entry_Modifier");

            // Write other concept-level metadata
            for (String meta : termbase.metadata) {
                writeCSV(out, meta);
            }

            for (Map.Entry<String, Integer> languageEntry : termbase.languages.entrySet()) {
                writeCSV(out, languageEntry.getKey() + "_Def");
                if (synonym == Synonym.COLUMN) {
                    for (int i = 0; i < languageEntry.getValue(); i++) {
                        writeCSV(out, languageEntry.getKey());
                        writeCSV(out, "Term_Info");
                        writeCSV(out, "Term_Example");
                    }
                } else if (synonym == Synonym.PIPE) {
                    writeCSV(out, languageEntry.getKey());
                }
            }
            out.write(NEW_LINE);

            // ========================== write rows =======================
            // for each concept
            for (Map.Entry<Integer, Concept> conceptEntry : termbase.concepts.entrySet()) {

                writeCSV(out, conceptEntry.getValue().getCreationTime());
                writeCSV(out, conceptEntry.getValue().getCreator());
                writeCSV(out, conceptEntry.getValue().getModificationTime());
                writeCSV(out, conceptEntry.getValue().getModifier());

                for (String meta : termbase.metadata) {
                    writeCSV(out, conceptEntry.getValue().getMeta(meta));
                }

                // Each language
                for (Map.Entry<String, Integer> languageEntry : termbase.languages.entrySet()) {
                    if (conceptEntry.getValue().termGroups.get(languageEntry.getKey()) != null) {
                        writeCSV(out, conceptEntry.getValue().termGroups.get(languageEntry.getKey())
                                .getDefinition());
                        // Write all synonyms in one language
                        if (synonym == Synonym.COLUMN) {
                            for (Term storedterm : conceptEntry.getValue().termGroups
                                    .get(languageEntry.getKey()).terms) {
                                writeCSV(out, storedterm.getWord());
                                writeCSV(out, storedterm.getTermInfo());
                                writeCSV(out, storedterm.getUsage());
                            }
                            // Fill up with empty cells
                            for (int i = 0; i < languageEntry.getValue()
                                    - conceptEntry.getValue().termGroups.get(languageEntry.getKey()).terms
                                            .size(); i++) {
                                out.write(outputType.sep + outputType.sep + outputType.sep);
                            }
                        } else if (synonym == Synonym.PIPE) {
                            String termsWithPipes = "";
                            for (Term storedterm : conceptEntry.getValue().termGroups
                                    .get(languageEntry.getKey()).terms) {
                                if (storedterm.getTermInfo().equals("NonTerm")) {
                                    termsWithPipes += "(NOT: " + storedterm.getWord() + ")|";
                                } else {
                                    termsWithPipes = storedterm.getWord() + '|' + termsWithPipes;
                                }
                            }
                            // Remove the last pipe
                            termsWithPipes = termsWithPipes.substring(0, termsWithPipes.length() - 1);
                            writeCSV(out, termsWithPipes);
                            termsWithPipes = "";
                        }
                    } else { // If no terms in given language, fill up with
                             // empty cells
                        out.write(outputType.sep); // For definition
                        if (synonym == Synonym.COLUMN) {
                            for (int i = 0; i < languageEntry.getValue(); i++) {
                                out.write(outputType.sep + outputType.sep + outputType.sep);
                            }
                        } else if (synonym == Synonym.PIPE) {
                            out.write(outputType.sep);
                        }
                    }
                }

                out.write("\n");
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getLocalizedMessage());
        }
    }

    private TermBase extractTermBase(File sdltbFile)
            throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder xmldb = dbf.newDocumentBuilder();
        Document document;

        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();

        Map<String, XPathExpression> xpathExpr = new HashMap<>();
        xpathExpr.put("entryCreator", xpath.compile("/cG/trG/tr[@type='origination']"));
        xpathExpr.put("creationTime", xpath.compile("/cG/trG/tr[@type='origination']/../dt"));
        xpathExpr.put("entryModifier", xpath.compile("/cG/trG/tr[@type='modification']"));
        xpathExpr.put("modificationTime", xpath.compile("/cG/trG/tr[@type='modification']/../dt"));
        xpathExpr.put("lang", xpath.compile("l/@type"));
        xpathExpr.put("conceptMeta", xpath.compile("/cG/dG"));
        xpathExpr.put("langData", xpath.compile("/cG/lG"));

        Database db = DatabaseBuilder.open(sdltbFile);
        Table table = db.getTable("mtConcepts");
        TermBase termbase = new TermBase();

        // Store contents in String xml
        for (Row row : table) {
            // Create new concept
            int entryNumber = Integer.parseInt(row.get("conceptid").toString());
            termbase.addConcept(entryNumber);
            String xml = row.get("text").toString();

            InputSource source = new InputSource(new StringReader(xml));
            document = xmldb.parse(source);

            // ==================== Read entry level data
            // =======================

            String entryCreator = xpathExpr.get("entryCreator").evaluate(document);
            String creationTime = xpathExpr.get("creationTime").evaluate(document);
            String entryModifier = xpathExpr.get("entryModifier").evaluate(document);
            String modificationTime = xpathExpr.get("modificationTime").evaluate(document);

            termbase.concepts.get(entryNumber).setEntryCreator(entryCreator);
            termbase.concepts.get(entryNumber).setCreationTime(creationTime);
            termbase.concepts.get(entryNumber).setEntryModifier(entryModifier);
            termbase.concepts.get(entryNumber).setModificationTime(modificationTime);

            // Process other concept-level metadata
            NodeList conceptMetaNodes = (NodeList) xpathExpr.get("conceptMeta").evaluate(document,
                    XPathConstants.NODESET);
            for (int h = 0; h < conceptMetaNodes.getLength(); h++) {
                Element g = (Element) conceptMetaNodes.item(h).getFirstChild();

                termbase.inMeta(g.getAttribute("type"));
                termbase.concepts.get(entryNumber).addMeta(g.getAttribute("type"), g.getTextContent());
            }

            // ================== Read language level data
            // ======================
            NodeList nodeList = (NodeList) xpathExpr.get("langData").evaluate(document,
                    XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) { // For each
                                                             // language group
                // Read language
                String lang = xpathExpr.get("lang").evaluate(nodeList.item(i));
                lang = lang.replaceAll(" ", "_");
                lang = lang.replaceAll("\\(|\\)", "");

                termbase.concepts.get(entryNumber).addTermgroup(lang);
                NodeList elements = nodeList.item(i).getChildNodes(); // l, dG,
                                                                      // tG
                for (int j = 0; j < elements.getLength(); j++) {
                    if (elements.item(j).getNodeName().equals("dG")) {
                        NodeList forbiddenOrDef = elements.item(j).getChildNodes(); // d
                        for (int k = 0; k < forbiddenOrDef.getLength(); k++) {
                            if (forbiddenOrDef.item(k).getNodeName().equals("d")) {
                                Element f = (Element) forbiddenOrDef.item(k);
                                if (f.getAttribute("type").equals("Forbidden term")) {
                                    Term term = new Term(forbiddenOrDef.item(k).getTextContent());
                                    termbase.concepts.get(entryNumber).addTerm(term, lang);
                                    term.addTermInfo("NonTerm");
                                } else if (f.getAttribute("type").equals("Definition")) {
                                    String definition = forbiddenOrDef.item(k).getTextContent();
                                    termbase.concepts.get(entryNumber).addDef(definition, lang);
                                }
                            }
                        }
                    } else if (elements.item(j).getNodeName().equals("tG")) {
                        Term term = new Term(xpath.evaluate("t", elements.item(j)));
                        termbase.concepts.get(entryNumber).addTerm(term, lang);

                        NodeList terms = elements.item(j).getChildNodes(); // t
                                                                           // (term),
                                                                           // trG
                                                                           // (metadata),
                                                                           // dG
                                                                           // (Usage
                                                                           // example)

                        // ================= Read term level data
                        // ===================
                        for (int l = 0; l < terms.getLength(); l++) {

                            if (terms.item(l).getNodeName().equals("dG")) {
                                NodeList usage = terms.item(l).getChildNodes(); // d
                                for (int m = 0; m < usage.getLength(); m++) {
                                    if (usage.item(m).getNodeName().equals("d")) {
                                        Element g = (Element) usage.item(m);
                                        if (g.getAttribute("type").equals("Usage example")) {
                                            term.addUsage(usage.item(m).getTextContent());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Populate languages
        for (Map.Entry<Integer, Concept> conceptEntry : termbase.concepts.entrySet()) { // for
                                                                                        // each
                                                                                        // concept
            for (Map.Entry<String, TermGroup> termgroupEntry : conceptEntry.getValue().termGroups
                    .entrySet()) { // for
                                   // each
                                   // termgroup
                if (termbase.inLanguageList(termgroupEntry.getKey()) < termgroupEntry.getValue().terms
                        .size()) {
                    termbase.setMaxNumber(termgroupEntry.getKey(), termgroupEntry.getValue().terms.size());
                }
            }
        }

        return termbase;
    }

    private void writeCSV(Writer out, String s) {
        try {
            switch (outputType) {
            case COMMA_CSV:
            case SEMICOLON_CSV:
                out.write('"');
                out.write(s.replaceAll("\"", "\"\""));
                out.write('"');
                out.write(outputType.sep);
                break;
            case TAB_TXT:
            case OMEGAT:
                out.write(s);
                out.write(outputType.sep);
                break;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e.getLocalizedMessage());
        }
    }

}
