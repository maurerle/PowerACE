package tools.logging;

import static simulations.scheduling.Date.DAYS_PER_YEAR;
import static simulations.scheduling.Date.HOURS_PER_YEAR;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import simulations.initialization.Settings;
import simulations.scheduling.Date;
import tools.file.Operations;
import tools.logging.LogFile.Frequency;

/**
 * Produces standardized log files
 *
 * @author
 */

public final class LoggerXLSX {

	private static final Map<Integer, CellStyle> cellStylesDouble = new ConcurrentHashMap<>();
	private static final Map<Integer, CellStyle> cellStylesInteger = new ConcurrentHashMap<>();
	private static boolean compress = false;
	private static final Map<Integer, Map<Sheet, Integer>> currentRows = new ConcurrentHashMap<>();
	private static String decimalFormat = "#,##0.00";
	// One thread is enough for logging
	private static final ExecutorService exec = Executors.newSingleThreadExecutor();
	private static final Map<Integer, String> fileNames = new ConcurrentHashMap<>();
	private static final Object lock = new Object(); // NOPMD
	private static final int numberOfMultipleFilesMax = 50;
	/**
	 * Instance of logger to give out warnings, errors to console and or files
	 */
	private static final Logger logger = LoggerFactory // NOPMD
			.getLogger(LoggerXLSX.class.getName());

	// Do not set to 0, cause that is that standard value for all
	// ints and therefore of the unitialized logids in all classes. If it is
	// checked via contains
	// if the logid of a class has been set a false positive is returned, if
	// set contains
	// the key zero, which is the case if logObjectID starts with 0 and
	// there has been already one log.
	private static int logObjectID = 100;
	private static final Map<Integer, SXSSFWorkbook> logObjects = new ConcurrentHashMap<>();

	/**
	 * Checks whether in the specified workbook a sheet with the given name
	 * already exisits
	 *
	 * @param logIDInvestmentOptionsXLSX
	 *            ID of log file
	 * @param worksheetName
	 *            Name of sheet
	 */
	public static boolean checkSheet(int logID, String sheetName) {
		if (getWorksheet(logID, sheetName) == null) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Closes the file
	 *
	 * @param logID
	 */
	public static void close(int logID) {
		close(logID, false, false);
	}

	public static void close(int logID, boolean forceCompression) {
		close(logID, forceCompression, false);
	}

	public static void close(int logID, boolean forceCompression, boolean writeYearly) {
		exec.execute(() -> {

			final String threadName = "LoggerXLSX for ID" + logID + "yearly? " + writeYearly
					+ " compress? " + forceCompression;
			Thread.currentThread().setName(threadName);

			final String fileName = fileNames.get(logID) + Settings.LOG_FILE_SUFFIX_EXCEL;
			try {
				Path file = Paths.get(fileName);
				// Check for other filenames if current one already existing
				// until maximum number of files are reached
				if (Files.exists(file) && !writeYearly) {
					for (int index = 0; index < numberOfMultipleFilesMax; index++) {
						final String alternativeFilename = fileNames.get(logID) + index
								+ Settings.LOG_FILE_SUFFIX_EXCEL;
						final Path alternativeFile = Paths.get(alternativeFilename);

						if (!Files.exists(alternativeFile)) {
							file = alternativeFile;
							break;
						}
					}
				}

				final SXSSFWorkbook workBook = logObjects.get(logID);
				// Create file, overwrite if existing (per default)
				final OutputStream out = Files.newOutputStream(file);
				workBook.write(out);
				out.close();
				workBook.close();
				workBook.dispose();

				if (!writeYearly) {
					// Remove temporary files
					removeLogIDFromLists(logID);
				} else {
					readFileAfterWriting(logID, file);
				}

				// Compress large files, effect for xlsx files is rather small
				// (10-25%)
				if (forceCompression || (compress && (Files.size(file) > 1000000))) {
					Operations.compressFile(file);
				}
			} catch (final Exception e) {
				logger.error("Error occured for file " + fileName);
				logger.error(e.getMessage(), e);
			}
		});

	}

	/**
	 * Write all log files and wait for shutdown. Create a new instance of the
	 * Executor in case of multiruns.
	 */
	public static void closeFinal() {
		try {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * Closes the file
	 *
	 * @param logID
	 */
	public static void closeYearly(int logID) {
		close(logID, false, true);
	}

	/**
	 * Writes the data (either as String or Number) to the logFile
	 * <p>
	 * Use this method when log file only has one worksheet.
	 *
	 * @param logID
	 * @param values
	 * @param text
	 */
	public static boolean contains(int logID) {
		return logObjects.get(logID) != null ? true : false;
	}

	/**
	 * Create new worksheet in all already initialized file
	 *
	 * @param logID
	 * @param description
	 * @param sheetName
	 * @param columns
	 * @param frequency
	 */
	public static void createNewSheet(int logID, String description, String sheetName,
			List<ColumnHeader> columns, Frequency frequency) {
		LoggerXLSX.createNewSheet(logID, description, sheetName, columns, frequency, decimalFormat);
	}

	/**
	 * Create new worksheet in all already initialized file
	 *
	 * @param logID
	 * @param description
	 * @param sheetName
	 * @param columns
	 * @param frequency
	 * @param decimalFormat
	 */
	public static void createNewSheet(int logID, String description, String sheetName,
			List<ColumnHeader> columns, Frequency frequency, String decimalFormat) {

		// Get Workbook
		final SXSSFWorkbook workBook = logObjects.get(logID);

		// Create new sheet
		final SXSSFSheet sheet = workBook.createSheet(sheetName);
		if (!currentRows.containsKey(logID)) {
			currentRows.put(logID, new HashMap<Sheet, Integer>());
		}
		sheet.setRandomAccessWindowSize(-1);

		// Set default width
		sheet.setDefaultColumnWidth(16);

		// First column with date can be shorter (unit is different from
		// default colum width)
		sheet.setColumnWidth(0, 2000);

		// Get number of columns
		final int columnsNumber = columns.size();
		final int rowNumber = LoggerXLSX.getRowsNumber(frequency);

		// Settings
		sheet.createFreezePane(1, 3);
		sheet.setZoom(80);
		if (columnsNumber <= 0) {
			logger.error("Number of columns needs to be larger than 0! " + fileNames.get(logID)
					+ "/" + sheetName);
		} else {
			sheet.setAutoFilter(new CellRangeAddress(2, rowNumber, 0, columnsNumber - 1));
		}

		currentRows.get(logID).put(sheet, 0);

		// Set Header font
		final Font fontHeader = workBook.createFont();
		fontHeader.setFontHeightInPoints((short) 11); // NOPMD
		fontHeader.setBold(true);
		fontHeader.setFontName("Arial");
		final CellStyle cellStyleHeader = workBook.createCellStyle();
		cellStyleHeader.setFont(fontHeader);

		// Set Row font
		final Font fontRow = workBook.createFont();
		fontRow.setFontHeightInPoints((short) 10); // NOPMD
		fontRow.setFontName("Arial");
		final CellStyle cellStyleRowDouble = workBook.createCellStyle();
		cellStyleRowDouble.setFont(fontRow);
		final DataFormat dataFormat = workBook.createDataFormat();
		cellStyleRowDouble.setDataFormat(dataFormat.getFormat(decimalFormat));
		cellStylesDouble.put(logID, cellStyleRowDouble);

		final CellStyle cellStyleRowInteger = workBook.createCellStyle();
		cellStyleRowInteger.setFont(fontRow);
		cellStylesInteger.put(logID, cellStyleRowInteger);

		Row row = LoggerXLSX.getRow(logID, sheet);
		final List<String> valueList = Arrays.asList(description.split(";"));
		for (int index = 0; index < valueList.size(); index++) {
			final Cell cell = row.createCell(index);
			cell.setCellStyle(cellStyleHeader);
			cell.setCellValue(valueList.get(index));
		}

		// For title line add vertical more space and align everything on top
		final CellStyle titleLineStyle = workBook.createCellStyle();
		titleLineStyle.setFont(fontRow);
		titleLineStyle.setVerticalAlignment(VerticalAlignment.TOP);
		titleLineStyle.setWrapText(true);
		row = LoggerXLSX.getRow(logID, sheet);
		row.setHeightInPoints((3 * sheet.getDefaultRowHeightInPoints()));

		for (int index = 0; index < columns.size(); index++) {
			final Cell cell = row.createCell(index);
			cell.setCellStyle(titleLineStyle);
			cell.setCellValue(LoggerXLSX.toTitleCase(columns.get(index).getColumnTitle()));
		}

		row = LoggerXLSX.getRow(logID, sheet);
		for (int index = 0; index < columns.size(); index++) {
			final Cell cell = row.createCell(index);
			cell.setCellStyle(cellStyleRowInteger);
			cell.setCellValue(columns.get(index).getColumnUnit());
		}
		// Set author
		final POIXMLProperties xmlProps = workBook.getXSSFWorkbook().getProperties();
		final POIXMLProperties.CoreProperties coreProps = xmlProps.getCoreProperties();
		coreProps.setCreator("PowerACE");
	}

	public static String getFilename(Integer logid) {
		return fileNames.get(logid);
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
	public static int newLogObject(Folder folder, String fileName, String description,
			List<ColumnHeader> columns, String marketArea, Frequency frequency) {
		return LoggerXLSX.newLogObject(folder, fileName, description, "Sheet0", columns, marketArea,
				frequency, decimalFormat);
	}

	/**
	 * Creates a new Logfile,writes the 3 standardized headers and assings a
	 * logID
	 *
	 * @param folder
	 * @param fileName
	 * @param description
	 * @param columns
	 * @param marketArea
	 * @param frequency
	 * @param decimalFormat
	 * @return
	 */
	public static int newLogObject(Folder folder, String fileName, String description,
			List<ColumnHeader> columns, String marketArea, Frequency frequency,
			String decimalFormat) {
		return LoggerXLSX.newLogObject(folder, fileName, description, "Sheet0", columns, marketArea,
				frequency, decimalFormat);
	}

	/**
	 * Creates a new Logfile,writes the 3 standardized headers and assings a
	 * logID
	 *
	 * @param fileName
	 * @param description
	 * @param sheetName
	 * @param titleLine
	 * @param unitLine
	 * @return
	 */
	public static int newLogObject(Folder folder, String fileName, String description,
			String sheetName, List<ColumnHeader> columns, String marketArea, Frequency frequency) {
		return LoggerXLSX.newLogObject(folder, fileName, description, sheetName, columns,
				marketArea, frequency, decimalFormat);
	}

	/**
	 * Creates a new Logfile,writes the 3 standardized headers and assings a
	 * logID
	 *
	 * @param fileName
	 * @param description
	 * @param sheetName
	 * @param titleLine
	 * @param unitLine
	 * @return
	 */
	public static int newLogObject(Folder folder, String fileName, String description,
			String sheetName, List<ColumnHeader> columns, String marketArea, Frequency frequency,
			String decimalFormat) {

		int logID = -1;
		try {

			// Create Workbook
			final SXSSFWorkbook workBook = new SXSSFWorkbook(1000);

			// Get new logID
			synchronized (lock) {
				logID = logObjectID++;
			}

			final String fileNameFull = Settings.getLogPathName(marketArea, folder) + fileName;
			// Put new logID in fields
			fileNames.put(logID, fileNameFull);
			logObjects.put(logID, workBook);

			// Create sheet
			LoggerXLSX.createNewSheet(logID, description, sheetName, columns, frequency,
					decimalFormat);

		} catch (final Exception e) {
			logger.error("Logfile: " + fileName + " could not be created.", e);
		}

		return logID;
	}

	/**
	 * Writes the data (either as String or Number) to the logFile
	 * <p>
	 * Use this method when log file only has one worksheet.
	 *
	 * @param logID
	 * @param values
	 * @param text
	 */
	public static void writeLine(int logID, List<Object> values) {
		final String worksheetName = logObjects.get(logID).getSheetAt(0).getSheetName();
		LoggerXLSX.writeLine(logID, worksheetName, values);
	}

	/**
	 * Writes the data (either as String or Number) to the logFile
	 * <p>
	 * Use this method when log file only has one worksheet and several threads
	 * will write in a similar sheet.
	 *
	 * @param logID
	 * @param values
	 * @param text
	 */
	public synchronized static void writeLineThreadSafe(int logID, List<Object> values) {
		final String worksheetName = logObjects.get(logID).getSheetAt(0).getSheetName();
		LoggerXLSX.writeLine(logID, worksheetName, values);
	}

	/**
	 * Writes the data (either as String or Number) to the logFile
	 * <p>
	 * Use this method when log file has several worksheets.
	 *
	 * @param logID
	 * @param worksheetName
	 * @param values
	 * @param text
	 */
	public static void writeLine(int logID, String worksheetName, List<Object> values) {
		try {
			final Sheet workSheet = getWorksheet(logID, worksheetName);
			final Row row = LoggerXLSX.getRow(logID, workSheet);

			int index = 0;
			for (final Object object : values) {
				final Cell cell = row.createCell(index++);
				// Cellstyle cannot be set generally for whole sheet and has to
				// be set for each style

				if (object == null) {
					// leave field empty at the moment
				} else if (object instanceof Number) {
					cell.setCellStyle(cellStylesDouble.get(logID));
					double value = ((Number) object).doubleValue();
					// Round in order save space
					if (!(object instanceof Integer) && Double.isFinite(value)) {
						value = new BigDecimal(value).setScale(4, RoundingMode.HALF_UP)
								.doubleValue();
					}
					cell.setCellValue(value);
					// Integer does not number after comma
					if (object instanceof Integer) {
						cell.setCellStyle(cellStylesInteger.get(logID));
					}
				} else {
					cell.setCellValue(object.toString());
				}

			}
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * Increases row of log file and returns row before increase.
	 *
	 * @param logID
	 * @param sheetID
	 * @return current row
	 */
	private static Row getRow(int logID, Sheet sheet) {

		// get increase row
		final int currentRow = currentRows.get(logID).get(sheet);
		currentRows.get(logID).put(sheet, currentRow + 1);
		final Row row = sheet.createRow(currentRow);

		return row;
	}

	private static int getRowsNumber(Frequency frequency) {
		final int NUMBER_OF_HEADER_ROWS = 3;

		if (frequency == Frequency.HOURLY) {
			return HOURS_PER_YEAR + NUMBER_OF_HEADER_ROWS;
		} else if (frequency == Frequency.DAILY) {
			return DAYS_PER_YEAR + NUMBER_OF_HEADER_ROWS;
		} else if (frequency == Frequency.YEARLY) {
			return Date.getNumberOfYears() + NUMBER_OF_HEADER_ROWS;
		} else if (frequency == Frequency.SIMULATION) {
			return 1 + NUMBER_OF_HEADER_ROWS;
		}
		return 0;
	}

	/**
	 * Returns the worksheet according to the logID and the woorksheetName.
	 *
	 * @param logID
	 * @param worksheetName
	 * @return current worksheet
	 */
	private static Sheet getWorksheet(int logID, String worksheetName) {
		final Sheet sheet = logObjects.get(logID).getSheet(worksheetName);
		return sheet;
	}

	private static void readFileAfterWriting(int logID, Path pathInput) throws IOException {

		// read already written file, must be input stream because of bug from
		// poi
		final XSSFWorkbook wbFile = new XSSFWorkbook(Files.newInputStream(pathInput));
		// Create new Workbook based on wb file
		final SXSSFWorkbook newWorkbook = new SXSSFWorkbook(wbFile);
		logObjects.put(logID, newWorkbook);

		// Remove old and add new sheet to map
		currentRows.get(logID).clear();
		// use physical number from XSSF, because its possible that new workbook
		// already flushed rows to the temp file on disk thats not reflected in
		// this method from SXSSF
		currentRows.get(logID).put(newWorkbook.getSheetAt(0),
				wbFile.getSheetAt(0).getPhysicalNumberOfRows());

	}

	private synchronized static void removeLogIDFromLists(int logID) {
		logObjects.remove(logID);
		fileNames.remove(logID);
		currentRows.remove(logID);
		cellStylesDouble.remove(logID);
		cellStylesInteger.remove(logID);
	}

	private static String toTitleCase(String input) {
		final StringBuilder titleCase = new StringBuilder();
		boolean nextTitleCase = true;

		for (char c : input.toCharArray()) {

			if (Character.isSpaceChar(c) || (c == '_')) {
				nextTitleCase = true;
				c = '_';
			} else if (nextTitleCase) {
				c = Character.toTitleCase(c);
				nextTitleCase = false;
			} else {
				c = Character.toLowerCase(c);
			}

			titleCase.append(c);
		}

		return titleCase.toString();
	}
}