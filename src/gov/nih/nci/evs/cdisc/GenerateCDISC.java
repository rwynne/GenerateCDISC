/* Generate CDISC reports from the Protege OWL export.
 * NCI/EVS with CDISC
 * Robert W. Wynne II (Medical Science & Computing)
 */

package gov.nih.nci.evs.cdisc;


import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;


import gov.nih.nci.evs.owl.data.OWLKb;
import gov.nih.nci.evs.owl.entity.Association;
import gov.nih.nci.evs.owl.entity.Property;
import gov.nih.nci.evs.owl.entity.Qualifier;
import gov.nih.nci.evs.owl.entity.Relationship;
import gov.nih.nci.evs.owl.proxy.ConceptProxy;
import gov.nih.nci.evs.reportwriter.formatter.AsciiToExcelFormatter;

public class GenerateCDISC {
	
	OWLKb kb = null;
	private final String namespace = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl";
	boolean checkShortNameLength = true;
	
	/**
	 * @param args OWL file location and report root concept
	 */
	public static void main(String args[]) {

		GenerateCDISC report = new GenerateCDISC();
		long start = System.currentTimeMillis();		
		System.out.println("Initializing OWLKb...");
		report.init(args[0]);
		System.out.println("Generating report...");
		
		//TODO: Iterate over a list of reports to generate	
		report.generate(args[1]);
		System.out.println("Finished report in "
		        + (System.currentTimeMillis() - start) / 1000 + " seconds.");		
	}
	
	/**
	 * @param filename
	 */
	public void init(String filename) {
		kb = new OWLKb(filename, namespace);		
	}
	
	public URI createURI(String code) {
		URI uri = null;
		try {
			uri = new URI(namespace + "#" + code);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return uri;	
	}
	
	/**
	 * @param root
	 * 			The root concept to report on
	 */
	public void generate(String root) {
		PrintWriter pw = null;
		File fil = null;
		String rootName = kb.getConcept(createURI(root)).getProperty("P108").getValue();
		
		//TODO: This may become CDISC_COA_Terminology - which is now referred to as QRS (Preferred_Name change)
		if( root.equals("CDISC_Questionnaire_Terminology") || root.equals("CDISC_Functional_Test_Terminology") || 
				root.equals("CDISC_Clinical_Classification_Terminology") || root.equals("CDISC_COA_Terminology")) {
			checkShortNameLength = false;
		}
		
		try {
			fil = new File(rootName + ".txt");
			pw = new PrintWriter(fil);
		} catch(Exception e) {
			System.out.println("Couldn't create output file.");
			System.exit(0);
		}		
		
		URI rootURI = null;
		try {
			rootURI = new URI(namespace + "#" + root);
		} catch (URISyntaxException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		HashMap<String,String> codelist2NCIAB = new HashMap<String,String>();
		HashMap<String,String> codelist2NCIPT = new HashMap<String,String>();
		HashMap<String,String> codelist2CDISCPT = new HashMap<String,String>();
		HashMap<String,String> codelist2CDISCSY = new HashMap<String,String>();
		TreeMap<String,String> cdiscsy2Codelist = new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER); 
		HashMap<String,String> codelist2Extensible = new HashMap<String,String>();
		HashMap<String,String> codelist2Code = new HashMap<String,String>();
		HashMap<String,String> codelist2Def = new HashMap<String,String>();
		HashMap<String,ArrayList<String>> codelist2Elements = new HashMap<String,ArrayList<String>>();
		Vector<URI> codelistConcepts = kb.getAllDescendantsForConcept(rootURI);
		
		codelistConcepts.remove("Nothing");
		
		for(URI codelistConcept : codelistConcepts ) {
			// System.out.println(codelistConcept);
//			Vector<String> synonyms = kb.getPropertyValues(codelistConcept, "FULL_SYN");
			Vector<Property> synonyms = kb.getConcept(codelistConcept).getProperties("P90");
			for( Property synonym : synonyms ) {
				String termName = synonym.getValue();
				String termSource = "";
				String termGroup = "";
				Vector<Qualifier> quals = synonym.getQualifiers();
				for( Qualifier qual : quals ) {
					if( qual.getName().equals("Term Source")) {
						termSource = qual.getValue();
					}
					if( qual.getName().equals("Term Type")) {
						termGroup = qual.getValue();
					}
				}
				if( termSource.equals("NCI") && termGroup.equals("PT") ) {
					codelist2NCIPT.put(codelistConcept.getFragment(), termName);
				}
				if( termSource.equals("NCI") && termGroup.equals("AB") ) {
					codelist2NCIAB.put(codelistConcept.getFragment(), termName);
				}
				if( termSource.equals("CDISC") && termGroup.equals("PT") ) {
					codelist2CDISCPT.put(codelistConcept.getFragment(), termName);
					//Per Erin: codelist submission value cannot be more than 8 characters in length
					if( checkShortNameLength && termName.length() > 8 ) {
						System.out.println("Warning: Codelist Submission Value (CDISC PT) over 8 characters - " + codelistConcept + " (" + termName + ")");
					}
				}
				if( termSource.equals("CDISC") && termGroup.equals("SY") ) {
					codelist2CDISCSY.put(codelistConcept.getFragment(), termName);
					if( !cdiscsy2Codelist.containsKey(termName) ) {
						cdiscsy2Codelist.put(termName, codelistConcept.getFragment());
					}
					else {
						System.out.println("There was an issue adding synonym " + termName);
					}
				}				
			}
			
			Vector<Property> exLists = kb.getConcept(codelistConcept).getProperties("P361");		
			//Vector<String> exLists = kb.getPropertyValues(codelistConcept, "Extensible_List");
			if( exLists.size() == 0 ) {
				System.out.println("No Extensible_List!\n\tCodelist concept: " + codelistConcept);
			}
			else if( exLists.size() > 1 ) {
				System.out.println("Multiple Extensible_List!\n\tCodelist concept: " + codelistConcept);
			}
			else {
				codelist2Extensible.put(codelistConcept.getFragment(), exLists.elementAt(0).getValue());
			}
			
			boolean foundDef = false;
			for( Property altDef : kb.getConcept(codelistConcept).getProperties("P325") ) {
				Vector<Qualifier> quals = altDef.getQualifiers();
				for( Qualifier qual : quals ) {
					//def-source
					if( qual.getName().equals("Definition Source") && qual.getValue().equals("CDISC")) {
						codelist2Def.put(codelistConcept.getFragment(), altDef.getValue());
						foundDef = true;
					}
				}
//				if( altDef.contains("<ncicp:def-source>CDISC</ncicp:def-source>") ) {
//					String cdiscDef = getQualVal(altDef, "ncicp:def-definition");
//					codelist2Def.put(codelistConcept, cdiscDef);
//					foundDef = true;
//				}
			}
			if( !foundDef ) {
				System.out.println("No CDISC Definition!\n\tCodelist concept: " + codelistConcept);
			}
			
			codelist2Code.put(codelistConcept.getFragment(), kb.getConcept(codelistConcept).getCode());
			

			
//			pw.print(codelist2Code.get(codelistConcept) + "\t");
//			pw.print("" + "\t");
//			pw.print(codelist2Extensible.get(codelistConcept) + "\t");
//			pw.print(codelist2CDISCSY.get(codelistConcept) + "\t");
//			pw.print(codelist2CDISCPT.get(codelistConcept) + "\t");
//			pw.print(codelist2CDISCSY.get(codelistConcept) + "\t");
//			pw.print(codelist2Def.get(codelistConcept) + "\t");
//			pw.print(codelist2NCIPT.get(codelistConcept) + "\n");	
		}
		
		System.out.println("Done phase 1");

//		URI testURI = null;
//		try {
//			testURI = new URI("http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C100000");
//		} catch (URISyntaxException e2) {
//			// TODO Auto-generated catch block
//			e2.printStackTrace();
//		}
//		Vector<Association> assocs1 = kb.getAssociationsForSource(testURI);
//		if( assocs1.size() > 0 ) {
//			for( Association assoc1 : assocs1 ) {
//				System.out.println(assoc1.getName() + " " + assoc1.getRelAndTarget() );
//			}
//		}
//		else {
//			System.out.println("No associations for " + testURI.getFragment());
//		}
		
		HashMap<URI,ConceptProxy> concepts =  kb.getAllConcepts();
		int m = 0;
		for( URI concept : concepts.keySet()) {
			m++;
			if( m % 100 == 0 ) {
//				System.out.println(m + " processed");
			}
//			System.out.println(concept.getFragment());
			Vector<Association> assocs = kb.getAssociationsForSource(concept);
			for(Relationship assoc : assocs ) {
//				if( assoc.toString().contains("Concept_In_Subset") ) {
//
//					
//					
//					String[] vals = assoc.toString().split(" ");
//					if( vals.length == 3 ) {
//						String codelistId = vals[2];
//						String element = vals[0];
//						if( codelistConcepts.contains(codelistId) ) {
//							if( codelist2Elements.containsKey(codelistId) ) {
//								ArrayList<String> tmp = codelist2Elements.get(codelistId);
//								if( !tmp.contains(element) ) {
//									tmp.add(element);
//								}
//								codelist2Elements.put(codelistId, tmp);
//							}
//							else {
//								ArrayList<String> tmp = new ArrayList<String>();
//								tmp.add(element);
//								codelist2Elements.put(codelistId, tmp);
//							}
//						}
//					}
//				} 
//				String name = assoc.getName();
				if (assoc.getName().equals("Concept_In_Subset")) {
					String element = assoc.getSource().getCode();
					String codelistId = assoc.getTarget().getCode();
					URI codelistURI = createURI(codelistId);
					if( codelistConcepts.contains(codelistURI) ) {
						if( codelist2Elements.containsKey(codelistId) ) {
							ArrayList<String> tmp = codelist2Elements.get(codelistId);
							if( !tmp.contains(element) ) {
								tmp.add(element);
							}
							codelist2Elements.put(codelistId, tmp);
						}
						else {
							ArrayList<String> tmp = new ArrayList<String>();
							tmp.add(element);							
							codelist2Elements.put(codelistId, tmp);
						}
					}
				}
			}
		}
		
		System.out.println("Done phase 2");

		
//		HSSFWorkbook wb = new HSSFWorkbook();		
//		String sheetName = root;
//		Sheet sheet = wb.createSheet(sheetName);
//		Row r = null;
//		Cell c = null;
//		HSSFCellStyle cs = wb.createCellStyle();
//		HSSFCellStyle cs2 = wb.createCellStyle();
//		HSSFCellStyle cs3 = wb.createCellStyle();
//		Font f = wb.createFont();
//		Font f2 = wb.createFont();
//		Font f3 = wb.createFont();
//		f.setFontHeightInPoints((short) 10);
//		f2.setFontHeightInPoints((short) 10);
//		f3.setBoldweight(Font.BOLDWEIGHT_BOLD);
//		f3.setFontHeightInPoints((short) 10);		
//		cs.setFont(f);
//		cs2.setFont(f2);
//		cs3.setFont(f3);
//		cs2.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
//		cs2.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);	
//		cs3.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
//		cs3.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);
//		int rownum = 0;
//		int cellnum = 0;
//		r = sheet.createRow(rownum);
		
		String[] header = { "Code", "Codelist Code", "Codelist Extensible (Yes/No)", "Codelist Name", "CDISC Submission Value", "CDISC Synonym(s)", "CDISC Definition", "NCI Preferred Term"};
		for( int i = 0; i < header.length; i++ ) {
			pw.print(header[i]);
			if( i + 1 < header.length ) pw.print("\t");
			else pw.print("\n");
		}
		
//		sheet.createFreezePane(0, 1, 0, 1);		
		
		for( String codelistName : cdiscsy2Codelist.keySet() ) {
			String codelistConcept = cdiscsy2Codelist.get(codelistName);
			
//			cellnum = 0;
//			r = sheet.createRow(++rownum);
//			
//			c = r.createCell(cellnum++);
//			c.setCellValue(codelist2Code.get(codelistConcept));
//			c.setCellStyle(cs2);
//			
//			c = r.createCell(cellnum++);
//			c.setCellValue("");
//			c.setCellStyle(cs2);
//			
//			c = r.createCell(cellnum++);
//			c.setCellValue(codelist2Extensible.get(codelistConcept));
//			c.setCellStyle(cs2);	
//			
//			c = r.createCell(cellnum++);
//			c.setCellValue(codelist2CDISCSY.get(codelistConcept));
//			c.setCellStyle(cs2);
//			
//			c = r.createCell(cellnum++);
//			c.setCellValue(codelist2CDISCPT.get(codelistConcept));
//			c.setCellStyle(cs2);
//			
//			c = r.createCell(cellnum++);
//			c.setCellValue(codelist2CDISCSY.get(codelistConcept));
//			c.setCellStyle(cs2);	
//			
//			c = r.createCell(cellnum++);
//			c.setCellValue(codelist2Def.get(codelistConcept));
//			c.setCellStyle(cs2);			
//			
//			c = r.createCell(cellnum++);
//			c.setCellValue(codelist2NCIPT.get(codelistConcept));
//			c.setCellStyle(cs2);			
			
			pw.print(codelist2Code.get(codelistConcept) + "\t");
			pw.print("" + "\t");
			pw.print(codelist2Extensible.get(codelistConcept) + "\t");
			pw.print(codelist2CDISCSY.get(codelistConcept) + "\t");
			pw.print(codelist2CDISCPT.get(codelistConcept) + "\t");
			pw.print(codelist2CDISCSY.get(codelistConcept) + "\t");
			pw.print(codelist2Def.get(codelistConcept) + "\t");
			pw.print(codelist2NCIPT.get(codelistConcept) + "\n");
			
			ArrayList<String> elements = codelist2Elements.get(codelistConcept);
			
			//Don't report retired elements
			for( int i=0; i < elements.size(); i++ ) {
				URI elementURI = null;
				try {
					elementURI = new URI(namespace + "#" + elements.get(i));
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if( kb.isDeprecated(elementURI) ) {
					elements.remove(i);
				}
			}
			
//			TreeMap<String,String> submission2Element = new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);
			TreeMap<String,String> submission2Element = new TreeMap<String,String>();			
			HashMap<String,String> element2Code = new HashMap<String,String>();
			HashMap<String,String> element2CodelistCode = new HashMap<String,String>();
			HashMap<String,ArrayList<String>> element2Synonyms = new HashMap<String,ArrayList<String>>();
			HashMap<String,String> element2Definition = new HashMap<String,String>();
			HashMap<String,String> element2PreferredName = new HashMap<String,String>();
			
			for( String element : elements ) {
				URI elementConcept = null;
				try {
					elementConcept = new URI(namespace + "#" + element);
				} catch (URISyntaxException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				element2Code.put(element, kb.getConcept(elementConcept).getCode());
				element2CodelistCode.put(element, codelist2Code.get(codelistConcept));
				element2PreferredName.put(element, kb.getConcept(elementConcept).getProperty("P108").getValue());
				
				Vector<Property> submissionValues = new Vector<Property>();
				ArrayList<String> cdiscSynonyms = new ArrayList<String>();				
				
				Vector<Property> synonyms = kb.getConcept(elementConcept).getProperties("P90");
				for( Property synonym : synonyms ) {
					String cdiscSy = synonym.getValue();
					String termSource = "";
					String termGroup = "";
					Vector<Qualifier> quals = synonym.getQualifiers();
					for( Qualifier qual : quals ) {
						if( qual.getName().equals("Term Source") ) {
							termSource = qual.getValue();
						}
						if( qual.getName().equals("Term Type") ) {
							termGroup = qual.getValue();
						}
					}
					if( termSource.equals("CDISC") && termGroup.equals("PT") ) {
						submissionValues.add(synonym);
					}
					if( termSource.equals("CDISC") && termGroup.equals("SY") ) {
						cdiscSynonyms.add(cdiscSy);
					}
				}
				
				Collections.sort(cdiscSynonyms);				
				element2Synonyms.put(element, cdiscSynonyms);
				if( submissionValues.size() > 1 ) {
					boolean found = false;
					for( Property possibleSubmissionValue : submissionValues ) {
						Vector<Qualifier> quals = possibleSubmissionValue.getQualifiers();
						for( Qualifier qual : quals ) {
							if( qual.getName().equals("Source Code") && qual.getValue().equals(codelist2NCIAB.get(codelistConcept)) ) {
								submission2Element.put(possibleSubmissionValue.getValue(), element);
								found = true;
							}
						}						
//						if( possibleSubmissionValue.contains("<ncicp:source-code>" + codelist2NCIAB.get(codelistConcept) + "</ncicp:source-code>") ) {
//							submission2Element.put(getQualVal(possibleSubmissionValue, "ncicp:term-name"), element);
//							found = true;
//						}
					}
					if( !found ) {
						System.out.print("No submission value!\n\tCodelist concept: "+ codelistConcept + "\n\tElement concept: " + element + "\n");
					}
				}
				else try {
					Property submissionValue = submissionValues.elementAt(0);
					submission2Element.put(submissionValue.getValue(), element);
				} catch (Exception e) {
					System.out.print("No submission value!\n\tCodelist concept: "+ codelistConcept + "\n\tElement concept: " + element + "\n");
				}
				
				
				Vector<Property> definitions = kb.getConcept(elementConcept).getProperties("P325");
				for( Property definition : definitions ) {
					Vector<Qualifier> quals = definition.getQualifiers();
					for( Qualifier qual : quals ) {
						if( qual.getName().equals("Definition Source") && qual.getValue().equals("CDISC")) {
							element2Definition.put(element, definition.getValue());
						}
					}
//					if( definition.contains("<ncicp:def-source>CDISC</ncicp:def-source>") ) {
//						element2Definition.put(element, getQualVal(definition, "ncicp:def-definition"));
//					}
				}								
			}
			
			
			ArrayList<String> keys = new ArrayList<String>();
			keys.addAll(submission2Element.keySet());
			Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
			
			for(String submission : keys ) {
				ArrayList<String> cdiscSynonyms = element2Synonyms.get(submission2Element.get(submission));
				String cellFormattedSynonyms = new String("");
				for( int i=0; i < cdiscSynonyms.size(); i++ ) {
					cellFormattedSynonyms = cellFormattedSynonyms.concat( cdiscSynonyms.get(i) );
					if( i + 1 < cdiscSynonyms.size() ) cellFormattedSynonyms = cellFormattedSynonyms.concat("; ");
				}
				
//				cellnum = 0;
//				r = sheet.createRow(++rownum);
//				
//				c = r.createCell(cellnum++);
//				c.setCellValue(element2Code.get(submission2Element.get(submission)));
//				c.setCellStyle(cs);
//				
//				c = r.createCell(cellnum++);
//				c.setCellValue(element2CodelistCode.get(submission2Element.get(submission)));
//				c.setCellStyle(cs);				
//				
//				c = r.createCell(cellnum++);
//				c.setCellValue("");
//				c.setCellStyle(cs);
//				
//				c = r.createCell(cellnum++);
//				c.setCellValue(codelistName);
//				c.setCellStyle(cs);
//				
//				c = r.createCell(cellnum++);
//				c.setCellValue(submission);
//				c.setCellStyle(cs);
//				
//				c = r.createCell(cellnum++);
//				c.setCellValue(cellFormattedSynonyms);
//				c.setCellStyle(cs);					
//				
//				c = r.createCell(cellnum++);
//				c.setCellValue(element2Definition.get(submission2Element.get(submission)));
//				c.setCellStyle(cs);	
//				
//				c = r.createCell(cellnum++);
//				c.setCellValue(element2PreferredName.get(submission2Element.get(submission)));
//				c.setCellStyle(cs);				
				
//				if( element2Code.get(submission2Element.get(submission)).equals("C74924") ) {
//				pw.print(element2Code.get(submission2Element.get(submission)) + "\t");
//				pw.print(element2CodelistCode.get(submission2Element.get(submission)) + "\t");
//				pw.print("" + "\t");
//				pw.print(codelistName + "\t");
//				pw.print(submission + "\t");
// 				pw.print(cellFormattedSynonyms);
//				pw.print("\t" + element2Definition.get(submission2Element.get(submission)) + "\t");
//				pw.print(element2PreferredName.get(submission2Element.get(submission)) + "\n");
//				}
//				else {
					pw.print(element2Code.get(submission2Element.get(submission)) + "\t");
					pw.print(element2CodelistCode.get(submission2Element.get(submission)) + "\t");
					pw.print("" + "\t");
					pw.print(codelistName + "\t");
					pw.print(submission + "\t");
	 				pw.print(cellFormattedSynonyms);
					pw.print("\t" + element2Definition.get(submission2Element.get(submission)) + "\t");
					pw.print(element2PreferredName.get(submission2Element.get(submission)) + "\n");				
//				}
			}
			
//			File fil = new File(root + ".xls");
//			try {
//				FileOutputStream out = new FileOutputStream(fil);
////				for( int i=0; i < 8; i++ ) sheet.autoSizeColumn(i);
//				wb.write(out);
//				out.close();				
//			} catch (IOException e) {
//				System.err.println("Couldn't create the Excel file. (Close if it is open.)");
//				System.exit(0);
//			}			
			
		}		

//		for( String codelistConcept : codelistConcepts ) {
//			System.out.println(codelistConcept);
//			if( codelist2Elements.containsKey(codelistConcept) ) {
//				ArrayList<String> tmp = codelist2Elements.get(codelistConcept);
//				Collections.sort(tmp);
//				System.out.print("\t");
//				for( String element : tmp ) {
//					System.out.print(element + "\t");
//				}
//				System.out.print("\n");
//			}		
//		}
		
		pw.close();
		
		AsciiToExcelFormatter formatter = new AsciiToExcelFormatter();
		try {
			formatter.convert(fil.toString(), "\t", fil.toString().replace(".txt", ".xls"));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		
	}

	/**
	 * @param fs Entire complex property
	 * @param qualName The qualifier name we want the value for
	 * @return qualifier value
	 */
	public String getQualVal(String fs, String qualName) {
		String val = new String("");
		int qualLength = qualName.length();
		qualLength += 2;
		int begin = fs.lastIndexOf("<" + qualName + ">");
		int last = fs.indexOf("</" + qualName + ">");
		val = fs.substring(begin+qualLength, last);
		val = val.trim().replace("<![CDATA[", "").replace("]]>", "");
		return val;
	}


}
