package tools.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.initialization.Settings;
import tools.file.Operations;

/**
 * Better use LoggerXLSX.
 *
 * Produces standardized log files
 *
 * @since 01.08.2005
 * @author 
 */
public final class LoggerCSV {

	private static boolean compress = true;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(LoggerCSV.class.getName());
	private static int logObjectID;
	private static List<PrintWriter> myLogObject;
	private static Map<Integer, String> myLogObjectFilenames;

	private static String addAfterLastSemicolon(String line) {

		if (line.endsWith(";")) {
			return line + "-";
		}
		if (line.endsWith("; ")) {
			return line + "-";
		}

		return line;

	}

	/**
	 * Closes the file
	 *
	 * @param logID
	 */
	public static void close(int logID) {
		if (logID != -1) {
			final PrintWriter out = myLogObject.get(logID);
			out.flush();
			out.close();

			final String fileName = myLogObjectFilenames.get(logID);
			final File file = new File(fileName);
			// Compress large files, csv files are very inefficient
			if (compress && (file.length() > 1000000)) {
				Operations.compressFile(file.toPath());
			}
		}
	}

	/**
	 * Creates the InfoLine for the standardized outputfile
	 *
	 * @param fileName
	 * @param description
	 * @return
	 */
	private static String createInfoLine(String fileName, String description) {
		String infoLine;
		infoLine = "#Logfile: " + fileName + " contains " + description + ".";
		return infoLine;
	}

	/**
	 * Creates the TitleLine for the standardized output file
	 *
	 * @param fileName
	 * @param description
	 * @return
	 */
	public static String createTitleLine(String timeCategory, String[] Categories) {
		String titleLine;
		titleLine = timeCategory;
		for (final String categorie : Categories) {
			titleLine = titleLine + ";" + categorie;
		}
		return titleLine;
	}

	public static void initialize() {

		logger.info("Initialize LoggerCSV");
		myLogObject = new ArrayList<>();
		myLogObjectFilenames = new HashMap<>();
		logObjectID = 0;
	}

	/**
	 * Creates a new Logfile,writes the 3 standardized headers and assings a
	 * logID
	 *
	 * @param fileName
	 * @param description
	 * @param titleLine
	 * @param unitLine
	 * @return
	 */
	public synchronized static int newLogObject(Folder folder, String fileName, String description,
			String titleLine, String unitLine, String marketArea) {

		PrintWriter out;

		// Create log folder
		final String path = Settings.getLogPathName(marketArea, folder);
		final String fileNameFull = path + fileName;
		try {
			final Writer writer = new FileWriter(fileNameFull, true);
			out = new PrintWriter(writer);
		} catch (final Exception e) {
			logger.error("Logfile: " + fileName + " could not be created.", e);
			return -1;
		}

		final String infoLine = LoggerCSV.createInfoLine(fileName, description);
		out.println(infoLine);
		out.println(LoggerCSV.addAfterLastSemicolon(titleLine));
		out.println(LoggerCSV.addAfterLastSemicolon(unitLine));
		final int logID = logObjectID++;
		myLogObject.add(logID, out);
		myLogObjectFilenames.put(logID, fileNameFull);
		out.flush();

		return logID;
	}

	public synchronized static int newLogObject(String fileName, String description,
			String titleLine, String unitLine, String marketArea) {
		return LoggerCSV.newLogObject(Folder.MAIN, fileName, description, titleLine, unitLine,
				marketArea);
	}

	private static String removeLastSemicolon(String line) {

		if (line.endsWith(";")) {
			return line.substring(0, line.length() - 1);
		}
		if (line.endsWith("; ")) {
			return line.substring(0, line.length() - 2);
		}

		return line;

	}

	/**
	 * Writes the dataline to the logFile
	 *
	 * @param logID
	 * @param text
	 */
	public static void writeLine(int logID, String text) {
		LoggerCSV.writeLine(logID, text, true);
	}

	/**
	 * Writes the dataline to the logFile
	 *
	 * @param logID
	 * @param text
	 */
	public static void writeLine(int logID, String text, boolean removeSemicolon) {
		if (logID != -1) {
			final PrintWriter out = myLogObject.get(logID);

			if (removeSemicolon) {
				text = LoggerCSV.removeLastSemicolon(text);
			}

			// Replace decimal delimiter symbol for German
			if ("de".equals(Settings.getLanguageSettings())) {
				out.println(text.replace('.', ','));
			} else {
				out.println(text);
			}
			out.flush();
		}
	}
}