package net.sf.jabref.imports;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.pdfbox.util.PDFTextStripperByArea;

import net.sf.jabref.BibtexEntry;
import net.sf.jabref.GUIGlobals;
import net.sf.jabref.Globals;
import net.sf.jabref.Util;

public class PdfContentImporter extends ImportFormat {
	
	private static Logger logger = Logger.getLogger(PdfContentImporter.class.getName());

	@Override
	public boolean isRecognizedFormat(InputStream in) throws IOException {
		return false;
	}

	/**
	 * Removes all non-letter characters at the end
	 * 
	 * EXCEPTION: a closing bracket is NOT removed
	 * 
	 * @param input
	 * @return
	 */
	private String removeNonLettersAtEnd(String input) {
		input = input.trim();
		char lastC = input.charAt(input.length()-1);
		while (!Character.isLetter(lastC) &&  (lastC!=')')) {
			// if there is an asterix, a dot or something else at the end: remove it
			input = input.substring(0, input.length()-1);
			if (input.length()>0) {
				lastC = input.charAt(input.length()-1);
			} else {
				break;
			}
		}
		return input;
	}
	
	private String streamlineNames(String names) {
		String res;
		// supported formats:
		//   Matthias Schrepfer1, Johannes Wolf1, Jan Mendling1, and Hajo A. Reijers2
		if (names.contains(",")) {
			String[] split = names.split(",");
			res = "";
			for (int i=0; i<split.length; i++) {
				String curName = removeNonLettersAtEnd(split[i]);
				if (curName.indexOf("and ")==0) {
					curName = curName.substring(4);
				}
				res = res.concat(curName);
				if (i!=split.length-1) {
					res = res.concat(" and ");
				}
			}
		} else {
			// names could be spearated by "and" - not treated here
			
			// assume: names separated by space
			// will fail at double names
			String[] split = names.split(" ");
			res = null;
			for (int i=0; i<split.length; i+=2) {
				if (i==0) {
					res = split[0];
					if (split.length>1) {
						res = res.concat(" ").concat(split[1]);
					}
				} else {
					res = res.concat(" and ").concat(split[i]);
					if (split.length>i+1) {
						res = res.concat(" ").concat(split[i+1]);
					}
				}
			}
			
		}
		return res;
	}
	
	private String streamlineTitle(String title) {
		return removeNonLettersAtEnd(title);
	}
	
	private boolean isYear(String yearStr) {
		try {
			Integer.parseInt(yearStr);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	
	@Override
	public List<BibtexEntry> importEntries(InputStream in) throws IOException {
		ArrayList<BibtexEntry> res = new ArrayList<BibtexEntry>(1);
		
		PDDocument document = null;
		try {
			document = PDDocument.load(in);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Could not load document", e);
			return res;
		}

		try {
			if (document.isEncrypted()) {
				logger.log(Level.INFO,
						Globals.lang("Encrypted documents are not supported"));
				return res;
			}

			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			stripper.setEndPage(1);
			stripper.setSortByPosition(true);
			stripper.setParagraphEnd(System.getProperty("line.separator"));
			StringWriter writer = new StringWriter();
			stripper.writeText(document, writer);
			String textResult = writer.toString();

			String author = null;
			String editor = null;
			String abstractT = null;
			String keywords = null;
			String title = null;
			String conference = null;
			String DOI = null;
			String series = null;
			String volume = null;
			String pages = null;
			String year = null;
			String publisher = null;
			
			final String lineBreak = System.getProperty("line.separator");
			
			String[] split = textResult.split(lineBreak);
			
			String curString = split[0].concat(" ");
			int i = 1;
			
			if (curString.length()>4) {
				// special case: possibly conference as first line on the page
				boolean match = false;
				if (isYear(curString.substring(0,4))) {
					year = curString.substring(0,4);
					match = true;
				}
				match = match || curString.contains("Conference");
				if (match) {
					while ((i<split.length)  && (!split[i] .equals(""))) {
						curString = curString.concat(split[i]).concat(" ");
						i++;
					}
					conference = curString;
					curString = "";
					i++;
				}
			}
			
			// start: title
			while ((i<split.length)  && (!split[i] .equals(""))) {
				curString = curString.concat(split[i]).concat(" ");
				i++;
			}
			title = streamlineTitle(curString);
			curString = "";
			i++;
			//i points to the next non-empty line
			// PDFTextStripper does NOT produce multiple empty lines (besides at strange PDFs)
			
			// special handling for strange PDFs which contain a line with " "
			while ((i<split.length) && (split[i].trim().equals("")))
				i++;
			
			// after title: authors
			while ((i<split.length)  && (!split[i].equals(""))) {
				curString = curString.concat(split[i]).concat(" ");
				i++;
			}
			author = streamlineNames(curString);
			curString = "";
			i++;
			
			// then, abstract and keywords follow
			while (i<split.length) {
				curString = split[i];
				if ((curString.length()>="Abstract".length()) && (curString.substring(0, "Abstract".length()).equalsIgnoreCase("Abstract"))) {
					curString = curString.substring("Abstract".length()+1).trim().concat(lineBreak);
					i++;
					while ((i<split.length)  && (!split[i] .equals(""))) {
						curString = curString.concat(split[i]).concat(lineBreak);
						i++;
					}
					abstractT=curString;
				} else if ((curString.length()>="Keywords".length()) && (curString.substring(0, "Keywords".length()).equalsIgnoreCase("Keywords"))) {
					curString = curString.substring("Keywords".length()+1).trim();
					i++;
					while ((i<split.length)  && (!split[i] .equals(""))) {
						curString = curString.concat(split[i]).concat(" ");
						i++;
					}
					keywords=removeNonLettersAtEnd(curString);
				}
				i++;
			}
			
			i = split.length-1;
			// last block: DOI, detailed information
			while ((i>0) && (!split[i].equals(""))) {
				i--;
			}			
			curString = "";
			if (i>0) {
				for (int j = i+1; j<split.length; j++) {
					curString = curString.concat(split[j]);
					if (j!=split.length-1) {
						curString = curString.concat(" ");
					}
				}
				int pos = curString.indexOf("(Eds.)");
				if (pos >= 0) {
					// looks like a Springer last line
					// e.g: A. Persson and J. Stirna (Eds.): PoEM 2009, LNBIP 39, pp. 161�175, 2009.
					publisher = "Springer";
					editor = streamlineNames(curString.substring(0, pos - 1));
					curString = curString.substring(pos+"(Eds.)".length()+2); //+2 because of ":" after (Eds.) and the subsequent space
					String[] springerSplit = curString.split(", ");
					if (springerSplit.length >= 4) {
						conference = springerSplit[0];

						String seriesData = springerSplit[1];
						int lastSpace = seriesData.lastIndexOf(' ');
						series = seriesData.substring(0, lastSpace);
						volume = seriesData.substring(lastSpace + 1);
						
						pages = springerSplit[2].substring(4);
						
						if (springerSplit[3].length()>=4) {
							year = springerSplit[3].substring(0,4);
						}
					}
				} else {
					pos = curString.indexOf("DOI");
					if (pos < 0) pos = curString.indexOf("doi");
					if (pos>=0) {
						pos += 3;
						char delimiter = curString.charAt(pos);
						if ((delimiter == ':') || (delimiter == ' ')) {
							pos++;
						}
						int nextSpace = curString.indexOf(' ', pos);
						if (nextSpace > 0)
							DOI = curString.substring(pos, nextSpace);
						else
							DOI = curString.substring(pos);
					}
					if (curString.indexOf("IEEE")>=0) {
						// IEEE has the conference things at the end
						publisher = "IEEE";
						
						String yearStr = curString.substring(curString.length()-4);
						if (isYear(yearStr)) {
							year = yearStr;
						}
						
						pos = curString.indexOf('$');
						if (pos>0) {
							// we found the price
							// before the price, the ISSN is stated
							// skip that
							pos -= 2;
							while ((pos>=0) && (curString.charAt(pos) != ' '))
								pos--;
							if (pos>0) {
								conference = curString.substring(0,pos);
							}
						}
					}
				}
			}

			BibtexEntry entry = new BibtexEntry();
			
			if (author!=null) entry.setField("author", author);
			if (editor!=null) entry.setField("editor", editor);
			if (abstractT!=null) entry.setField("abstract", abstractT);
			if (keywords!=null) entry.setField("keywords", keywords);
			if (title!=null) entry.setField("title", title);
			if (conference!=null) entry.setField("booktitle", conference);
			if (DOI!=null) entry.setField("doi", DOI);
			if (series!=null) entry.setField("series", series);
			if (volume!=null) entry.setField("volume", volume);
			if (pages!=null) entry.setField("pages", pages);
			if (year!=null) entry.setField("year", year);
			if (publisher!=null) entry.setField("publisher", publisher);
			
			entry.setField("review", textResult);

			res.add(entry);
		} finally {
			document.close();
		}
		
		return res;
	}

	@Override
	public String getFormatName() {
		return "PDFcontent";
	}

}